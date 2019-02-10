package com.zendesk.jazon

import com.zendesk.jazon.actual.ActualJsonArray
import com.zendesk.jazon.actual.ActualJsonNull
import com.zendesk.jazon.actual.ActualJsonNumber
import com.zendesk.jazon.actual.ActualJsonObject
import com.zendesk.jazon.actual.ActualJsonString
import com.zendesk.jazon.actual.ActualTranslators
import com.zendesk.jazon.expectation.ExpectationTranslators
import com.zendesk.jazon.mismatch.*
import spock.lang.Specification
import spock.lang.Unroll

class FacadeExpectationSpec extends Specification {

    @Unroll
    def "primitive value mismatch (expected: #expected, actual: #actual)"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: actual])

        then:
        !result.ok()
        result.mismatch() == new PrimitiveValueMismatch(expected, actual)

        where:
        expected                | actual
        123                     | 10
        123                     | 130.1f
        123                     | 1500.13d
        123                     | new BigDecimal("11.05")
        123                     | 12345l
        130.1f                  | 10
        130.1f                  | 133.3f
        130.1f                  | 1500.13d
        130.1f                  | new BigDecimal("11.05")
        130.1f                  | 12345l
        1500.13d                | 10
        1500.13d                | 130.1f
        1500.13d                | 1555.55d
        1500.13d                | new BigDecimal("11.05")
        1500.13d                | 12345l
        new BigDecimal("11.05") | 10
        new BigDecimal("11.05") | 130.1f
        new BigDecimal("11.05") | 1500.13d
        new BigDecimal("11.05") | new BigDecimal("11.11")
        new BigDecimal("11.05") | 12345l
        12345l                  | 10
        12345l                  | 130.1f
        12345l                  | 1500.13d
        12345l                  | new BigDecimal("11.05")
        12345l                  | 1234567l
        'something serious'     | 'lol'
    }

    def "simple primitive type mismatch"() {
        when:
        def result = new FacadeExpectation(expected).match(actual)

        then:
        !result.ok()
        result.mismatch() == new TypeMismatch(mismatchExpectedType, mismatchActualType)

        where:
        expected      | actual        | mismatchExpectedType   | mismatchActualType
        [a: 123]      | [a: 'lol']    | ActualJsonNumber.class | ActualJsonString.class
        [a: 'ww']     | [a: 88]       | ActualJsonString.class | ActualJsonNumber.class
        [a: 123]      | [a: [bb: 10]] | ActualJsonNumber.class | ActualJsonObject.class
        [a: 'ww']     | [a: [bb: 10]] | ActualJsonString.class | ActualJsonObject.class
        [a: [bb: 10]] | [a: 88]       | ActualJsonObject.class | ActualJsonNumber.class
        [a: [bb: 10]] | [a: 'lol']    | ActualJsonObject.class | ActualJsonString.class
    }

    def "finds null instead of primitive value"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: null])

        then:
        !result.ok()
        result.mismatch() == new NullMismatch(expectedType, expected)

        where:
        expected                | expectedType
        123                     | ActualJsonNumber
        130.1f                  | ActualJsonNumber
        1500.13d                | ActualJsonNumber
        new BigDecimal("11.05") | ActualJsonNumber
        12345l                  | ActualJsonNumber
        "sting"                 | ActualJsonString
    }

    @Unroll
    def "mismatch in object field (expected: #expectedFieldValue, actual: #actualFieldValue)"() {
        given:
        def expected = [
                a: 103,
                b: expectedFieldValue
        ]
        def actual = [
                a: 103,
                b: actualFieldValue,
        ]

        when:
        def result = new FacadeExpectation(expected).match(actual)

        then:
        !result.ok()
        result.mismatch() == foundMismatch

        where:
        expectedFieldValue | actualFieldValue || foundMismatch
        'vegetable'        | 'meat'           || new PrimitiveValueMismatch('vegetable', 'meat')
        'vegetable'        | null             || new NullMismatch<>(ActualJsonString, 'vegetable')
        'vegetable'        | 150              || new TypeMismatch(ActualJsonString, ActualJsonNumber)
        77                 | 'rosemary'       || new TypeMismatch(ActualJsonNumber, ActualJsonString)
        []                 | 'kek'            || new TypeMismatch(ActualJsonArray, ActualJsonString)
        [20, 30]           | [20, 77]         || new ArrayElementMismatch(1, new PrimitiveValueMismatch(30, 77))
    }

    def "catches lacking field in Object"() {
        given:
        def expected = [
                a: 103,
                b: 'some value',
        ]

        when:
        def result = new FacadeExpectation(expected).match(actual)

        then:
        !result.ok()
        result.mismatch() == new NullMismatch(ActualJsonString, 'some value');

        where:
        actual << [
                [a: 103],
                [a: 103, c: 'kek']
        ]
    }

    @Unroll
    def "catches unexpected field in Object: #unexpectedFieldValue"() {
        given:
        def expected = [
                a: 103,
                c: 'Chicago',
        ]
        def actual = [
                a: 103,
                b: unexpectedFieldValue,
                c: 'Chicago',
        ]

        when:
        def result = new FacadeExpectation(expected).match(actual)

        then:
        !result.ok()
        result.mismatch() == new UnexpectedFieldMismatch(unexpectedFieldType);

        where:
        unexpectedFieldValue    | unexpectedFieldType
        'act of vandalism'      | ActualJsonString
        123                     | ActualJsonNumber
        1999l                   | ActualJsonNumber
        20.14f                  | ActualJsonNumber
        44.999d                 | ActualJsonNumber
        new BigDecimal("80.92") | ActualJsonNumber
        [a: 1, b: 'lol']        | ActualJsonObject
        null                    | ActualJsonNull
        [5, 4, 3]               | ActualJsonArray
    }

    @Unroll
    def "ordered list expectation - exact element mismatch"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: actual])

        then:
        !result.ok()
        result.mismatch() == new ArrayElementMismatch(elementIndex, elementMismatch)

        where:
        expected  | actual    || elementIndex | elementMismatch
        [1, 2, 3] | [3, 2, 1] || 0            | new PrimitiveValueMismatch<>(1, 3)
        [1, 2, 3] | [1, 7, 3] || 1            | new PrimitiveValueMismatch<>(2, 7)
    }

    @Unroll
    def "ordered list expectation - lacking elements (expected: #expected, actual: #actual)"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: actual])

        then:
        !result.ok()
        result.mismatch() == new ArrayLackingElementsMismatch(
                lackingElements.collect(ExpectationTranslators.&expectation)
        )

        where:
        expected                  | actual          || lackingElements
        [1, 2, 3]                 | [1, 2]          || [3]
        [1, 2, 'lalala']          | [1, 2]          || ['lalala']
        [1, 2, 'lalala', 5, 6, 7] | [1, 2]          || ['lalala', 5, 6, 7]
//        [1, 2, null, 5, 6, 7]     | [1, 2, null, 5] || [6, 7]
//        [1, null]                 | [1]             || [null]
//        [9]                       | []               | [9]
//        [null]                    | []               | [null]
//        [null, 'kek', 17]         | []               | [null, 'kek', 17]
    }

    def "ordered list expectation - unexpected elements"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: actual])

        then:
        !result.ok()
        result.mismatch() == new ArrayUnexpectedElementsMismatch(
                unexpectedElements.collect(ActualTranslators.&actual)
        )

        where:
        expected | actual            || unexpectedElements
        [1, 2]   | [1, 2, 3]         || [3]
        []       | [1, 2, 3]         || [1, 2, 3]
        ['beka'] | ['beka', 'czeka'] || ['czeka']
    }

    def "unordered list expectation: success"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: actual])

        then:
        result.ok()

        where:
        expected                         | actual
        [1, 2, 3] as Set                 | [1, 3, 2]
        [1, 2, 3] as Set                 | [3, 1, 2]
        [1, 2, 'lalala'] as Set          | ['lalala', 1, 2]
        [1, 2, 'lalala', 5, 6, 7] as Set | [6, 7, 2, 5, 'lalala', 1]
    }

    @Unroll
    def "unordered list expectation: lacking elements (actual: #actual)"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: actual])

        then:
        !result.ok()
        result.mismatch() == new ArrayLackingElementsMismatch(
                lackingElements.collect(ExpectationTranslators.&expectation)
        )

        where:
        expected                         | actual                 || lackingElements
        [1, 2, 3] as Set                 | [1, 3]                 || [2]
        [1, 2, 3] as Set                 | [1, 3, 1]              || [2]
        [1, 2, 3] as Set                 | [3, 1, 1, 88]          || [2]
        [1, 2, 3] as Set                 | [3, 1, 3, 1]           || [2]
        [1, 2, 'lalala'] as Set          | ['lalala', 11, 2]      || [1]
        [1, 2, 'lalala', 5, 6, 7] as Set | [6, 7, 2, 5, 'rob', 1] || ['lalala']
        [3, 4, 2, 1] as Set              | [11, 2, 3, 55]         || [1, 4]
    }

    def "unordered list expectation: unexpected elements"() {
        when:
        def result = new FacadeExpectation([a: expected]).match([a: actual])

        then:
        !result.ok()
        result.mismatch() == new ArrayUnexpectedElementsMismatch(
                unexpectedElements.collect(ActualTranslators.&actual)
        )

        where:
        expected                      | actual                     || unexpectedElements
        [1, 2, 3] as Set              | [1, 3, 2, 8]               || [8]
        [1, 2, 3] as Set              | [1, 3, 2, 'sushi']         || ['sushi']
        [1, 2, 3] as Set              | [1, 'sushi', 2, 3]         || ['sushi']
        [1, 2, 3] as Set              | [1, 3, 2, null]            || [null]
        ['what', 'is', 'love'] as Set | ['love', 'is', 10, 'what'] || [10]
    }

    def "unordered list expectation: fails for unsupported expectation types"() {
        given:
        def unsupportedExpectation = [1, 2, 3] as Set
        def unorderedArrayExpectationWrapping = ['fish', 'chips', unsupportedExpectation] as Set
        def theWholeExpectation = [a: unorderedArrayExpectationWrapping]

        when:
        new FacadeExpectation(theWholeExpectation)

        then:
        thrown(IllegalStateException)
    }

    def "null expectation: fails for any present value"() {
        when:
        def result = new FacadeExpectation([a: null]).match([a: actual])

        then:
        !result.ok()
        result.mismatch() == new NotNullMismatch(ActualTranslators.actual(actual))

        where:
        actual << [
                'something',
                10,
                130.1f,
                1555.55d,
                new BigDecimal("11.05"),
                12345l,
                [x: 123],
                [1, 2, 3]
        ]
    }

    def "null expectation: succeeds for null"() {
        when:
        def result = new FacadeExpectation([a: null]).match([a: null])

        then:
        result.ok()
    }
}
