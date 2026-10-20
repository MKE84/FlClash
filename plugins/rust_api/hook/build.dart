import 'dart:io';

import 'package:code_assets/code_assets.dart';
import 'package:flutter_rust_bridge_hooks/flutter_rust_bridge_hooks.dart';

void main(List<String> args) async {
  await build(args, (input, output) async {
    if (input.userDefines['build_assets'] == false) {
      stdout.writeln('Skipping the Rust build: user-define build_assets=false');
      return;
    }
    await FlutterRustBridgeNativeAssetsBuilder(
      cratePath: 'rust',
      extraCargoEnvironmentVariables: _bindgenEnvironment(input),
    ).run(input: input, output: output);
  });
}

// rquickjs runs bindgen on Android, which must load the NDK's libclang; Linux
// NDKs before r26 keep it under lib64, later ones and every macOS NDK under lib.
Map<String, String> _bindgenEnvironment(BuildInput input) {
  if (!input.config.buildCodeAssets ||
      input.config.code.targetOS != OS.android) {
    return const {};
  }
  final compiler = input.config.code.cCompiler?.compiler;
  if (compiler == null) {
    return const {};
  }
  final llvmRoot = File.fromUri(compiler).parent.parent;
  // A host without clang of its own reports a compiler in the NDK that carries
  // none, so fall back to the NDK directory Flutter reported.
  final ndk = input.config.code.ndk?.path;
  final candidates = <Directory>[
    llvmRoot,
    if (ndk != null) Directory('$ndk/toolchains/llvm/prebuilt'),
  ];
  for (final root in candidates) {
    final found = _libclang(root);
    if (found != null) return {'LIBCLANG_PATH': found.path};
  }
  throw StateError(
    'No libclang under ${llvmRoot.path} (lib or lib64); the NDK Flutter '
    'passed cannot run bindgen for rquickjs',
  );
}

/// The first `lib` or `lib64` under [root] that holds a libclang, if any.
/// [root] is either an llvm directory or `toolchains/llvm/prebuilt`, whose
/// children are one llvm directory per host.
Directory? _libclang(Directory root) {
  if (!root.existsSync()) return null;
  final hosts = [
    root,
    for (final entry in root.listSync())
      if (entry is Directory && !entry.path.endsWith('/bin')) entry,
  ];
  for (final host in hosts) {
    for (final name in const ['lib', 'lib64']) {
      final directory = Directory(
        '${host.path}${Platform.pathSeparator}$name',
      );
      if (directory.existsSync() && directory.listSync().any(_isLibclang)) {
        return directory;
      }
    }
  }
  return null;
}

bool _isLibclang(FileSystemEntity entity) {
  return entity.path.split(Platform.pathSeparator).last.startsWith('libclang.');
}
