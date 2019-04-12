/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geb.navigator

import geb.navigator.factory.NavigatorFactory
import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebElement

import java.util.function.Supplier

import static geb.navigator.Locator.MATCH_ALL_SELECTOR

class SearchContextBasedBasicLocator implements BasicLocator {

    private static final Map<String, Closure> BY_SELECTING_ATTRIBUTES = [
            id   : By.&id,
            class: By.&className,
            name : By.&name
    ]
    public static final String DYNAMIC_ATTRIBUTE_NAME = "dynamic"
    public static final List<String> NON_SELECTOR_TRANSLATABLE_ATTRIBUTES = ["text", DYNAMIC_ATTRIBUTE_NAME]

    private final Iterable<? extends SearchContext> searchContexts
    private final NavigatorFactory navigatorFactory

    SearchContextBasedBasicLocator(SearchContext searchContext, NavigatorFactory navigatorFactory) {
        this([searchContext], navigatorFactory)
    }

    SearchContextBasedBasicLocator(Iterable<? extends SearchContext> searchContexts, NavigatorFactory navigatorFactory) {
        this.searchContexts = searchContexts
        this.navigatorFactory = navigatorFactory
    }

    @Override
    Navigator find(boolean dynamic = false, By bySelector) {
        def elementsSupplier = elementsSupplier(bySelector)
        navigatorFor(dynamic ? toDynamicIterable(elementsSupplier) : elementsSupplier.get())
    }

    @Override
    Navigator find(Map<String, Object> attributes, String selector) {
        def attributesCopy = attributes.clone()
        def selectedUsingBy = findUsingByIfPossible(attributesCopy, selector)
        if (selectedUsingBy != null) {
            return selectedUsingBy
        }
        def optimizedSelector = optimizeSelector(selector, attributesCopy)
        optimizedSelector ? find(attributes[DYNAMIC_ATTRIBUTE_NAME].asBoolean(), By.cssSelector(optimizedSelector)).filter(attributesCopy) : find(attributes)
    }

    protected Navigator navigatorFor(Iterable<WebElement> contextElements) {
        navigatorFactory.createFromWebElements(contextElements)
    }

    protected Navigator findUsingByIfPossible(Map<String, Object> attributes, String selector) {
        if (attributes.size() == 1 && selector == MATCH_ALL_SELECTOR) {
            BY_SELECTING_ATTRIBUTES.findResult {
                if (hasStringValueForKey(attributes, it.key)) {
                    find(it.value.call(attributes[it.key]))
                }
            }
        }
    }

    protected boolean hasStringValueForKey(Map<String, Object> attributes, String key) {
        attributes.containsKey(key) && attributes[key] instanceof String
    }

    /**
     * Optimizes the selector by translating attributes map into a css attribute selector if possible.
     * Note this method has a side-effect in that it _removes_ those keys from the predicates map.
     */
    protected String optimizeSelector(String selector, Map<String, Object> attributes) {
        if (!selector) {
            return selector
        }

        def buffer = new StringBuilder(selector)
        for (def it = attributes.entrySet().iterator(); it.hasNext();) {
            def attribute = it.next()
            if (!(attribute.key in NON_SELECTOR_TRANSLATABLE_ATTRIBUTES) && attribute.value instanceof CharSequence) {
                def attributeValue = attribute.value.toString()
                if (attribute.key == "class") {
                    attributeValue.split(/\s+/).each { className ->
                        buffer << "." << CssSelector.escape(className)
                    }
                } else {
                    buffer << """[${attribute.key}="${CssSelector.escape(attributeValue)}"]"""
                }
                it.remove()
            }
        }

        if (buffer[0] == MATCH_ALL_SELECTOR && buffer.length() > 1) {
            buffer.deleteCharAt(0)
        }
        buffer.toString()
    }

    protected Supplier<Collection<WebElement>> elementsSupplier(By bySelector) {
        { ->
            searchContexts.collectMany { it.findElements(bySelector) }
        }
    }

    protected Iterable<WebElement> toDynamicIterable(Supplier<Collection<WebElement>> contextElementsSupplier) {
        { -> contextElementsSupplier.get().iterator() } as Iterable<WebElement>
    }
}
