#!/usr/bin/env python3
"""Build a small self-targeted platform instrumentation APK; never sign or modify ePlayer."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--platform', default='android-36')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not args.sdk:
        parser.error('Android SDK path is required')
    def java_tool(name):
        tool = Path(args.java_home) / 'bin' / name if args.java_home else shutil.which(name)
        if not tool or not Path(tool).is_file():
            raise RuntimeError('A JDK with javac/keytool is required; set JAVA_HOME or --java-home')
        return tool
    sdk = Path(args.sdk)
    platform = sdk / 'platforms' / args.platform
    android = platform / 'android.jar'
    libraries = [android, platform / 'optional/android.test.runner.jar', platform / 'optional/android.test.base.jar']
    if not all(path.is_file() for path in libraries):
        raise RuntimeError('Install the compile platform including optional test libraries')
    build = sdk / 'build-tools/36.0.0'
    source = Path(__file__).resolve().parents[1] / 'qa/eplayer'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='eplayer-qa-') as directory:
        folder = Path(directory)
        classes, dex = folder / 'classes', folder / 'dex'
        classes.mkdir(); dex.mkdir()
        run(java_tool('javac'), '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-classpath', os.pathsep.join(map(str, libraries)),
            '-d', classes, source / 'R8MusicCheck.java')
        command = [build / 'd8', '--min-api', '36', '--output', dex]
        for library in libraries:
            command += ['--lib', library]
        run(*command, *sorted(classes.rglob('*.class')))
        unsigned, aligned = folder / 'unsigned.apk', folder / 'aligned.apk'
        run(build / 'aapt2', 'link', '-I', android, '--manifest', source / 'AndroidManifest.xml', '-o', unsigned)
        with zipfile.ZipFile(unsigned, 'a') as archive:
            archive.write(dex / 'classes.dex', 'classes.dex')
        run(build / 'zipalign', '-f', '4', unsigned, aligned)
        key = folder / 'qa.keystore'
        run(java_tool('keytool'), '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android', '-alias', 'qa',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '365', '-dname', 'CN=ePlayer QA')
        run(build / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', 'qa', '--ks-pass', 'pass:android',
            '--key-pass', 'pass:android', '--out', args.output.resolve(), aligned)
        run(build / 'apksigner', 'verify', args.output.resolve())
    print('Built isolated QA APK:', args.output)


if __name__ == '__main__':
    main()
