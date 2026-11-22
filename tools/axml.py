#!/usr/bin/env python3
"""Bongkar AndroidManifest.xml biner (AXML) tanpa SDK.

Menampilkan setiap tag start beserta atribut name/value-nya, supaya bisa dipastikan
`uses-feature` benar-benar berisi `aniyomix.extension` (bukan meta-data atau atribut lain).
"""
import sys
import zipfile
import struct


class Axml:
    def __init__(self, data: bytes):
        self.d = data
        self.strings: list[str] = []
        self.res_ids: dict[int, str] = {}
        self._parse_strings()
        self._parse_resmap()

    def _parse_strings(self):
        d = self.d
        if struct.unpack_from('<H', d, 8)[0] != 0x0001:
            raise ValueError('bukan AXML')
        # chunk header: type uint16, headerSize uint16, size uint32, count uint32, ...
        count = struct.unpack_from('<I', d, 8 + 8)[0]
        off = 8 + 28
        offsets = struct.unpack_from(f'<{count}I', d, off)
        base = off + count * 4
        for o in offsets:
            p = base + o
            n = struct.unpack_from('<H', d, p)[0]  # panjang karakter
            raw = d[p + 2:p + 2 + n * 2]
            self.strings.append(raw.decode('utf-16-le', 'ignore'))

    def _parse_resmap(self):
        d = self.d
        p = 8
        while p < len(d) - 8:
            ctype, hsize, size = struct.unpack_from('<HHI', d, p)
            if ctype == 0x0180:  # RESOURCE MAP
                n = (size - hsize) // 4
                for i in range(n):
                    self.res_ids[struct.unpack_from('<I', d, p + hsize + i * 4)[0]] = None
                break
            if size <= 0:
                break
            p += size

    def tags(self):
        d = self.d
        p = 8
        while p < len(d) - 8:
            ctype, hsize, size = struct.unpack_from('<HHI', d, p)
            if size <= 0 or p + size > len(d):
                break
            if ctype == 0x0102:  # START TAG
                body = p + hsize
                ns, name = struct.unpack_from('<II', d, body)
                attr_start, attr_size, attr_count = struct.unpack_from('<HHH', d, body + 8)
                attrs = []
                for i in range(attr_count):
                    q = body + attr_start + i * attr_size
                    a_ns, a_name, a_raw = struct.unpack_from('<III', d, q)
                    a_type = d[q + 15]
                    a_data = struct.unpack_from('<I', d, q + 16)[0]
                    nm = self.strings[a_name] if a_name < len(self.strings) else f'?{a_name}'
                    if a_raw != 0xFFFFFFFF and a_raw < len(self.strings):
                        val = self.strings[a_raw]
                    elif a_type == 0x12:  # boolean
                        val = 'true' if a_data else 'false'
                    elif a_type == 0x10:
                        val = str(a_data)
                    else:
                        val = f'0x{a_data:x}'
                    attrs.append((nm, val))
                tname = self.strings[name] if name < len(self.strings) else f'?{name}'
                yield tname, attrs
            p += size


def main(path: str):
    if path.endswith('.apk'):
        data = zipfile.ZipFile(path).read('AndroidManifest.xml')
    else:
        data = open(path, 'rb').read()
    ax = Axml(data)
    for tag, attrs in ax.tags():
        if tag in ('manifest', 'uses-feature', 'uses-permission', 'meta-data', 'application', 'uses-sdk'):
            kv = ' '.join(f'{k}={v!r}' for k, v in attrs)
            print(f'<{tag}> {kv}')


if __name__ == '__main__':
    main(sys.argv[1])