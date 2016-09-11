/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OrderSensitive;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;

public interface CopySpecInternal extends CopySpec {

    //TODO - does this belong here or on the resolver? PEZ
    boolean hasSource();

    @OrderSensitive
    @Nested(resolveCollections = true)
    Iterable<CopySpecInternal> getChildren();

    @Optional @Input
    String getDestPath();

    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    @InputFiles
    FileTree getSource();

    CopySpecInternal addChild();

    CopySpecInternal addChildBeforeSpec(CopySpecInternal childSpec);

    CopySpecInternal addFirst();

    void walk(Action<? super CopySpecResolver> action);

    CopySpecResolver buildRootResolver();

    CopySpecResolver buildResolverRelativeToParent(CopySpecResolver parent);

}
