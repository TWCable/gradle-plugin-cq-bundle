/*
 * Copyright 2014-2017 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twcable.gradle.sling;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link SlingSupportFactory} that simply calls {@link SlingSupport#SlingSupport(SlingServerConfiguration)}
 *
 * @see SimpleSlingSupportFactory#INSTANCE
 */
public class SimpleSlingSupportFactory implements SlingSupportFactory {

    /**
     * Singleton implementation of {@link SimpleSlingSupportFactory}
     */
    public static final SimpleSlingSupportFactory INSTANCE = new SimpleSlingSupportFactory();


    private SimpleSlingSupportFactory() {
    }


    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public SlingSupport create(@Nonnull SlingServerConfiguration serverConfiguration) {
        if (serverConfiguration == null) throw new IllegalArgumentException("serverConfiguration == null");
        return new SlingSupport(serverConfiguration);
    }

}
