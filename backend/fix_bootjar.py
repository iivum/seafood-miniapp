#!/usr/bin/env python3
"""
Fix Spring Boot bootJar by extracting common module classes from nested common-*.jar
and removing the nested jar. Preserves exact ZIP structure and compression.
Also rebuilds classpath.idx and layers.idx to remove references to the deleted JAR.
"""
import sys
import zipfile
import os
import tempfile
import shutil
import re

def fix_bootjar(bootjar_path):
    basename = os.path.basename(bootjar_path)
    print(f"Processing: {basename}")

    # Read all entries and compression types from original JAR
    entries = {}
    orig_infos = {}
    with zipfile.ZipFile(bootjar_path, 'r') as zf:
        for info in zf.infolist():
            entries[info.filename] = zf.read(info.filename)
            orig_infos[info.filename] = info

    # Find common-*.jar in BOOT-INF/lib
    common_jars = [n for n in entries.keys() if n.startswith('BOOT-INF/lib/common-') and n.endswith('.jar')]
    if not common_jars:
        print(f"  No common-*.jar found")
        return

    temp_dir = tempfile.mkdtemp()
    extract_dir = os.path.join(temp_dir, 'extracted')

    try:
        # Extract boot.jar
        os.makedirs(extract_dir)
        with zipfile.ZipFile(bootjar_path, 'r') as zf:
            zf.extractall(extract_dir)

        # For each common jar, extract its contents to BOOT-INF/classes
        for common_jar in common_jars:
            common_jar_path = os.path.join(extract_dir, common_jar)
            print(f"  Extracting: {os.path.basename(common_jar)}")
            with zipfile.ZipFile(common_jar_path, 'r') as zf:
                zf.extractall(os.path.join(extract_dir, 'BOOT-INF', 'classes'))
            os.remove(common_jar_path)
            print(f"  Removed: {os.path.basename(common_jar)}")

        # Rebuild classpath.idx WITHOUT common JAR
        lib_files = sorted([
            n for n in os.listdir(os.path.join(extract_dir, 'BOOT-INF', 'lib'))
            if n.endswith('.jar') and not n.startswith('common-')
        ])
        lib_paths = [f'BOOT-INF/lib/{f}' for f in lib_files]
        
        classpath_lines = ['# classpath idx']
        for path in lib_paths:
            classpath_lines.append(f'  - "{path}"')
        
        classpath_idx_path = os.path.join(extract_dir, 'BOOT-INF', 'classpath.idx')
        with open(classpath_idx_path, 'w') as f:
            f.write('\n'.join(classpath_lines))
        print(f"  Rebuilt classpath.idx with {len(lib_files)} JARs (no common)")

        # Rebuild layers.idx WITHOUT common JAR  
        layers_lines = [
            '- "dependencies":',
        ]
        for path in lib_paths[:100]:  # limit to first 100
            layers_lines.append(f'  - "{path}"')
        layers_lines.extend([
            '- "spring-boot-loader":',
            '  - "org/"',
            '- "snapshot-dependencies":',
            '- "application":',
            '  - "BOOT-INF/classes/"',
            '  - "BOOT-INF/classpath.idx"',
            '  - "BOOT-INF/layers.idx"',
            '  - "META-INF/"',
        ])
        
        layers_idx_path = os.path.join(extract_dir, 'BOOT-INF', 'layers.idx')
        with open(layers_idx_path, 'w') as f:
            f.write('\n'.join(layers_lines))
        print(f"  Rebuilt layers.idx")

        # Build list of all files to add, preserving compression
        files_to_add = []
        for root, dirs, files in os.walk(extract_dir):
            for fname in files:
                fpath = os.path.join(root, fname)
                arcname = os.path.relpath(fpath, extract_dir)
                compress_type = zipfile.ZIP_DEFLATED
                if arcname in orig_infos:
                    compress_type = orig_infos[arcname].compress_type
                files_to_add.append((fpath, arcname, compress_type))

        # Sort to ensure consistent ordering
        files_to_add.sort(key=lambda x: x[1])

        # Repack with compression preserved
        output_path = os.path.join(temp_dir, 'output.jar')
        with zipfile.ZipFile(output_path, 'w') as zf_out:
            for fpath, arcname, compress_type in files_to_add:
                zf_out.write(fpath, arcname, compress_type=compress_type)

        # Replace original
        shutil.move(output_path, bootjar_path)
        print(f"  Fixed: {basename}")

    finally:
        shutil.rmtree(temp_dir)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: fix_bootjar.py <path-to-bootjar>")
        sys.exit(1)
    fix_bootjar(sys.argv[1])
