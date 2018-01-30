/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017:
 * 	Una Thompson (unascribed),
 * 	Isaac Ellingson (Falkreon),
 * 	Jamie Mansfield (jamierocks),
 * 	and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.elytradev.concrete.rulesengine;

import com.elytradev.concrete.common.Either;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class Example extends RulesEngine<Example.ExampleContext> {


    @NotNull
    @Override
    public Map<Character, Function1<String, Either<Predicate<ExampleContext>, String>>> getDomainPredicates() {
        return null;
    }

    @NotNull
    @Override
    public Set<Integer> getEffectSlots() {
        return null;
    }

    @NotNull
    @Override
    public Either<Iterable<Effect<ExampleContext>>, String> effect(@NotNull String s) {
        return null;
    }

    static class ExampleContext extends EvaluationContext {
        private int theInt;
    }
}
