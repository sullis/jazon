package com.zendesk.jazon.expectation;

import com.zendesk.jazon.actual.ActualJsonBoolean;
import com.zendesk.jazon.actual.ActualJsonNumber;
import com.zendesk.jazon.actual.ActualJsonString;
import groovy.lang.Closure;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.zendesk.jazon.expectation.ExpectationFactory.*;

public class SpockExpectationFactory implements ExpectationFactory {

    @Override
    public JsonExpectation expectation(Object object) {
        if (object instanceof Map) {
            return objectExpectation((Map<String, Object>) object, this);
        } else if (object instanceof Number) {
            return new PrimitiveValueExpectation<>((Number) object, ActualJsonNumber.class);
        } else if (object instanceof String) {
            return new PrimitiveValueExpectation<>((String) object, ActualJsonString.class);
        } else if (object instanceof Boolean) {
            return new PrimitiveValueExpectation<>((Boolean) object, ActualJsonBoolean.class);
        } else if (object instanceof List) {
            return expectedOrderedArray((List<Object>) object, this);
        } else if (object instanceof Set) {
            return expectedUnorderedArray((Set<Object>) object, this);
        } else if (object instanceof Closure) {
            Closure<Boolean> closure = (Closure<Boolean>) object;
            return new PredicateExpectation(closure::call);
        } else if (object == null) {
            return new NullExpectation();
        }
        throw new IllegalArgumentException();
    }
}