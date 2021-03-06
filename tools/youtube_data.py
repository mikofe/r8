# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import utils

ANDROID_L_API = '21'
ANDROID_M_API = '23'

BASE = os.path.join(utils.THIRD_PARTY, 'youtube')

V16_20_BASE = os.path.join(BASE, 'youtube.android_16.20')
V16_20_PREFIX = os.path.join(V16_20_BASE, 'YouTubeRelease')

V17_19_BASE = os.path.join(BASE, 'youtube.android_17.19')
V17_19_PREFIX = os.path.join(V17_19_BASE, 'YouTubeRelease')

LATEST_VERSION = '17.19'

VERSIONS = {
  '16.20': {
    'deploy' : {
      'sanitize_libraries': False,
      'inputs': ['%s_deploy.jar' % V16_20_PREFIX],
      'libraries' : [
          os.path.join(
              V16_20_BASE,
              'legacy_YouTubeRelease_combined_library_jars_filtered.jar')],
      'pgconf': [
          '%s_proguard.config' % V16_20_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY,
          utils.IGNORE_WARNINGS_RULES],
      'min-api' : ANDROID_L_API,
      'android_java8_libs': {
        'config': '%s/desugar_jdk_libs/full_desugar_jdk_libs.json' % V16_20_BASE,
        # Intentionally not adding desugar_jdk_libs_configuration.jar since it
        # is part of jdk_libs_to_desugar.jar in YouTube 16.20.
        'program': ['%s/desugar_jdk_libs/jdk_libs_to_desugar.jar' % V16_20_BASE],
        'library': '%s/android_jar/lib-v30/android.jar' % utils.THIRD_PARTY,
        'pgconf': [
          '%s/desugar_jdk_libs/base.pgcfg' % V16_20_BASE,
          '%s/desugar_jdk_libs/minify_desugar_jdk_libs.pgcfg' % V16_20_BASE
        ]
      }
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V16_20_PREFIX],
      'pgmap': '%s_proguard.map' % V16_20_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '17.19': {
    'deploy' : {
      'sanitize_libraries': False,
      'inputs': ['%s_deploy.jar' % V17_19_PREFIX],
      'libraries' : [
          os.path.join(
              V17_19_BASE,
              'legacy_YouTubeRelease_combined_library_jars_filtered.jar')],
      'pgconf': [
          '%s_proguard.config' % V17_19_PREFIX,
          '%s_proguard_extra.config' % V17_19_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY,
          utils.IGNORE_WARNINGS_RULES],
      'min-api' : ANDROID_M_API,
      'system-properties': [
          # TODO(b/235169948): Reenable -checkenumunboxed.
          # '-Dcom.android.tools.r8.experimental.enablecheckenumunboxed=1',
          '-Dcom.android.tools.r8.experimental.enableconvertchecknotnull=1'],
      'android_java8_libs': {
        'config': '%s/desugar_jdk_libs/full_desugar_jdk_libs.json' % V17_19_BASE,
        # Intentionally not adding desugar_jdk_libs_configuration.jar since it
        # is part of jdk_libs_to_desugar.jar in YouTube 17.19.
        'program': ['%s/desugar_jdk_libs/jdk_libs_to_desugar.jar' % V17_19_BASE],
        'library': '%s/android_jar/lib-v33/android.jar' % utils.THIRD_PARTY,
        'pgconf': [
          '%s/desugar_jdk_libs/base.pgcfg' % V17_19_BASE,
          '%s/desugar_jdk_libs/minify_desugar_jdk_libs.pgcfg' % V17_19_BASE
        ]
      }
    },
  },
}

def GetLatestVersion():
  return LATEST_VERSION

def GetName():
  return 'youtube'

def GetMemoryData(version):
  assert version == '16.20'
  return {
      'find-xmx-min': 3150,
      'find-xmx-max': 3300,
      'find-xmx-range': 64,
      'oom-threshold': 3100,
      # TODO(b/143431825): Youtube can OOM randomly in memory configurations
      #  that should work.
      'skip-find-xmx-max': True,
  }
