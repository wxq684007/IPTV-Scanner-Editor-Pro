"""Raw binary ELF stripper - removes .debug_*, .symtab, .strtab sections
and compacts the file to eliminate the dead space left behind.

Handles both 32-bit and 64-bit little-endian ELF files.
Key insight: debug sections often sit between loadable data and .shstrtab,
creating a large "hole". We shift sections after the hole to fill it,
then truncate the file.
"""

import struct
import os
import sys

SHT_NULL = 0
SHT_NOBITS = 8
PT_LOAD = 1

# Sections to remove (by name prefix or exact match)
REMOVE_PREFIXES = ('.debug_',)
REMOVE_EXACT = {'.symtab', '.strtab'}


def should_remove(name):
    return any(name.startswith(p) for p in REMOVE_PREFIXES) or name in REMOVE_EXACT


def strip_elf(filepath):
    before = os.path.getsize(filepath)
    with open(filepath, 'rb') as f:
        data = bytearray(f.read())

    if data[:4] != b'\x7fELF':
        return before, before

    is64 = data[4] == 2
    if data[5] != 1:  # not little-endian
        return before, before

    if is64:
        return _process(data, filepath, before, is64=True)
    else:
        return _process(data, filepath, before, is64=False)


def _parse_header(data, is64):
    """Parse ELF header, return dict of fields."""
    if is64:
        return {
            'e_phoff': struct.unpack_from('<Q', data, 0x20)[0],
            'e_shoff': struct.unpack_from('<Q', data, 0x28)[0],
            'e_phentsize': struct.unpack_from('<H', data, 0x36)[0],
            'e_phnum': struct.unpack_from('<H', data, 0x38)[0],
            'e_shentsize': struct.unpack_from('<H', data, 0x3A)[0],
            'e_shnum': struct.unpack_from('<H', data, 0x3C)[0],
            'e_shstrndx': struct.unpack_from('<H', data, 0x3E)[0],
        }
    else:
        return {
            'e_phoff': struct.unpack_from('<I', data, 0x1C)[0],
            'e_shoff': struct.unpack_from('<I', data, 0x20)[0],
            'e_phentsize': struct.unpack_from('<H', data, 0x2A)[0],
            'e_phnum': struct.unpack_from('<H', data, 0x2C)[0],
            'e_shentsize': struct.unpack_from('<H', data, 0x2E)[0],
            'e_shnum': struct.unpack_from('<H', data, 0x30)[0],
            'e_shstrndx': struct.unpack_from('<H', data, 0x32)[0],
        }


def _parse_sections(data, hdr, is64):
    """Parse all section headers."""
    sections = []
    for i in range(hdr['e_shnum']):
        off = hdr['e_shoff'] + i * hdr['e_shentsize']
        if is64:
            s = {
                'idx': i,
                'name_idx': struct.unpack_from('<I', data, off)[0],
                'type': struct.unpack_from('<I', data, off + 4)[0],
                'flags': struct.unpack_from('<Q', data, off + 8)[0],
                'addr': struct.unpack_from('<Q', data, off + 16)[0],
                'offset': struct.unpack_from('<Q', data, off + 24)[0],
                'size': struct.unpack_from('<Q', data, off + 32)[0],
                'link': struct.unpack_from('<I', data, off + 40)[0],
                'info': struct.unpack_from('<I', data, off + 44)[0],
                'addralign': struct.unpack_from('<Q', data, off + 48)[0],
                'entsize': struct.unpack_from('<Q', data, off + 56)[0],
            }
        else:
            s = {
                'idx': i,
                'name_idx': struct.unpack_from('<I', data, off)[0],
                'type': struct.unpack_from('<I', data, off + 4)[0],
                'flags': struct.unpack_from('<I', data, off + 8)[0],
                'addr': struct.unpack_from('<I', data, off + 12)[0],
                'offset': struct.unpack_from('<I', data, off + 16)[0],
                'size': struct.unpack_from('<I', data, off + 20)[0],
                'link': struct.unpack_from('<I', data, off + 24)[0],
                'info': struct.unpack_from('<I', data, off + 28)[0],
                'addralign': struct.unpack_from('<I', data, off + 32)[0],
                'entsize': struct.unpack_from('<I', data, off + 36)[0],
            }
        sections.append(s)
    return sections


def _parse_load_segments(data, hdr, is64):
    """Find end of all PT_LOAD segments."""
    load_end = 0
    for i in range(hdr['e_phnum']):
        off = hdr['e_phoff'] + i * hdr['e_phentsize']
        p_type = struct.unpack_from('<I', data, off)[0]
        if p_type != PT_LOAD:
            continue
        if is64:
            p_offset = struct.unpack_from('<Q', data, off + 8)[0]
            p_filesz = struct.unpack_from('<Q', data, off + 32)[0]
        else:
            p_offset = struct.unpack_from('<I', data, off + 4)[0]
            p_filesz = struct.unpack_from('<I', data, off + 16)[0]
        load_end = max(load_end, p_offset + p_filesz)
    return load_end


def _get_names(data, sections, shstrndx):
    """Resolve section names from the string table."""
    shstr = sections[shstrndx]
    strtab = data[shstr['offset']:shstr['offset'] + shstr['size']]
    for sec in sections:
        end = strtab.find(b'\x00', sec['name_idx'])
        sec['name'] = strtab[sec['name_idx']:end].decode('ascii', 'replace')


def _process(data, filepath, before, is64):
    hdr = _parse_header(data, is64)
    sections = _parse_sections(data, hdr, is64)
    _get_names(data, sections, hdr['e_shstrndx'])
    load_end = _parse_load_segments(data, hdr, is64)

    # Classify sections
    kept = []
    removed = []
    for i, sec in enumerate(sections):
        if should_remove(sec['name']):
            removed.append(sec)
        else:
            kept.append(sec)

    if not removed:
        return before, before

    # Find the removed region (contiguous block of removed sections with data)
    removed_with_data = [
        s for s in removed
        if s['type'] not in (SHT_NULL, SHT_NOBITS) and s['size'] > 0
    ]
    if not removed_with_data:
        # Removed sections have no file data, just rebuild headers
        return _rebuild(data, sections, kept, removed, hdr, is64,
                        filepath, before, shift=0)

    removed_start = min(s['offset'] for s in removed_with_data)

    # Find the end of kept data BEFORE the removed region
    kept_before_end = load_end
    for s in kept:
        if s['type'] not in (SHT_NULL, SHT_NOBITS) and s['size'] > 0:
            if s['offset'] + s['size'] <= removed_start:
                kept_before_end = max(kept_before_end, s['offset'] + s['size'])

    # Find ALL kept sections with data that start at or after removed_start.
    # These include sections INSIDE the removed region (e.g. .shstrtab
    # sandwiched between .symtab and .strtab) and sections after it.
    # They all need to be relocated to fill the gap left by removed sections.
    kept_to_move = sorted(
        [s for s in kept
         if s['type'] not in (SHT_NULL, SHT_NOBITS)
         and s['size'] > 0
         and s['offset'] >= removed_start],
        key=lambda s: s['offset']
    )

    if kept_to_move:
        # Relocate each section to right after the previous data
        write_pos = kept_before_end
        for s in kept_to_move:
            # Align to section's addralign (usually 1 for strtab)
            align = s.get('addralign', 1) or 1
            if align > 1:
                write_pos = (write_pos + align - 1) & ~(align - 1)
            # Copy section data to new position
            section_data = bytes(data[s['offset']:s['offset'] + s['size']])
            data[write_pos:write_pos + s['size']] = section_data
            s['offset'] = write_pos
            write_pos += s['size']

    return _rebuild(data, sections, kept, removed, hdr, is64,
                    filepath, before, shift=0)


def _rebuild(data, sections, kept, removed, hdr, is64,
             filepath, before, shift):
    """Rebuild section header table without removed sections,
    shift section header table offset, and truncate file."""

    removed_set = {s['idx'] for s in removed}

    # Build old-to-new index mapping
    old_to_new = {}
    for new_idx, sec in enumerate(kept):
        old_to_new[sec['idx']] = new_idx

    # Build new section header table
    new_shdr = bytearray()
    for sec in kept:
        off = hdr['e_shoff'] + sec['idx'] * hdr['e_shentsize']
        shdr = bytearray(data[off:off + hdr['e_shentsize']])

        # Update offset (sections may have been relocated)
        if sec.get('offset') is not None:
            if is64:
                struct.pack_into('<Q', shdr, 24, sec['offset'])
            else:
                struct.pack_into('<I', shdr, 16, sec['offset'])

        # Fix sh_link
        link_off = 40 if is64 else 24
        sh_link = struct.unpack_from('<I', shdr, link_off)[0]
        if sh_link in old_to_new:
            struct.pack_into('<I', shdr, link_off, old_to_new[sh_link])
        elif sh_link in removed_set:
            struct.pack_into('<I', shdr, link_off, 0)

        # Fix sh_info
        info_off = 44 if is64 else 28
        sh_info = struct.unpack_from('<I', shdr, info_off)[0]
        if sh_info in old_to_new:
            struct.pack_into('<I', shdr, info_off, old_to_new[sh_info])
        elif sh_info in removed_set:
            struct.pack_into('<I', shdr, info_off, 0)

        new_shdr.extend(shdr)

    # New e_shstrndx
    new_shstrndx = old_to_new.get(hdr['e_shstrndx'], 0)

    # Determine the end of all kept section data
    max_data_end = 0
    for sec in kept:
        if sec['type'] not in (SHT_NULL, SHT_NOBITS) and sec['size'] > 0:
            max_data_end = max(max_data_end, sec['offset'] + sec['size'])

    # Place new section header table after max_data_end (aligned to 8)
    new_shoff = (max_data_end + 7) & ~7

    # Build the new file
    new_data = bytearray(data[:max_data_end])

    # Pad to alignment
    while len(new_data) < new_shoff:
        new_data.append(0)

    # Append section header table
    new_data.extend(new_shdr)

    # Update ELF header
    if is64:
        struct.pack_into('<Q', new_data, 0x28, new_shoff)
        struct.pack_into('<H', new_data, 0x3C, len(kept))
        struct.pack_into('<H', new_data, 0x3E, new_shstrndx)
    else:
        struct.pack_into('<I', new_data, 0x20, new_shoff)
        struct.pack_into('<H', new_data, 0x30, len(kept))
        struct.pack_into('<H', new_data, 0x32, new_shstrndx)

    with open(filepath, 'wb') as f:
        f.write(new_data)

    after = len(new_data)
    return before, after


def main():
    base_dirs = sys.argv[1:] if len(sys.argv) > 1 else [
        r"android\app\src\main\jniLibs",
    ]

    total_b = total_a = 0
    for base in base_dirs:
        if not os.path.exists(base):
            print(f"Not found: {base}")
            continue
        for root, _, files in os.walk(base):
            for f in sorted(files):
                if not f.endswith('.so'):
                    continue
                path = os.path.join(root, f)
                rel = os.path.relpath(path)
                b, a = strip_elf(path)
                saved = b - a
                mb_b = b / 1048576
                mb_a = a / 1048576
                mb_s = saved / 1048576
                pct = saved / b * 100 if b else 0
                print(f"{rel:60s} {mb_b:6.1f} -> {mb_a:6.1f} MB  "
                      f"(saved {mb_s:5.1f} MB, {pct:.0f}%)")
                total_b += b
                total_a += a

    print(f"\n{'TOTAL':60s} {total_b / 1048576:6.1f} -> "
          f"{total_a / 1048576:6.1f} MB  "
          f"(saved {(total_b - total_a) / 1048576:5.1f} MB, "
          f"{(total_b - total_a) / total_b * 100:.0f}%)")


if __name__ == "__main__":
    main()
