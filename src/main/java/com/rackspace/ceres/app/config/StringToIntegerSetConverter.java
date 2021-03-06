/*
 * Copyright 2020 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.ceres.app.config;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Parses a string containing comma separated list of integers or ranges of integers,
 * such as "1,5-8,12,15-18"
 */
@Component
@ConfigurationPropertiesBinding
public class StringToIntegerSetConverter implements Converter<String, IntegerSet> {

  @Override
  public IntegerSet convert(String input) {
    if (!StringUtils.hasText(input)) {
      return new IntegerSet(Set.of());
    }

    final Pattern pattern = Pattern.compile("(?<range>(?<start>\\d+)-(?<end>\\d+))|(?<single>\\d+)");
    final String[] parts = input.split(",");

    final Set<Integer> partitions = new TreeSet<>();
    for (String part : parts) {
      final Matcher m = pattern.matcher(part);
      if (m.matches()) {
        if (m.group("single") != null) {
          partitions.add(Integer.parseInt(m.group("single")));
        } else {
          for (int i = Integer.parseInt(m.group("start")); i <= Integer.parseInt(m.group("end")); i++) {
            partitions.add(i);
          }
        }
      } else {
        throw new IllegalArgumentException("Invalid number list expression");
      }
    }

    return new IntegerSet(partitions);
  }
}
