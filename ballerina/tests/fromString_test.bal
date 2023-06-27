// Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/test;

// Possitive tests for fromStringWithType() function.

type INT int;

@test:Config
isolated function testStringToInt() returns error? {
    int intValue1 = check fromStringWithType("1");
    test:assertEquals(intValue1, 1);

    int intValue2 = check fromStringWithType("-1000");
    test:assertEquals(intValue2, -1000);

    INT intValue3 = check fromStringWithType("1");
    test:assertEquals(intValue3, 1);
}

type Float float;

@test:Config
isolated function testStringToFloat() returns error? {
    float floatValue1 = check fromStringWithType("2.012");
    test:assertEquals(floatValue1, 2.012);

    float floatValue2 = check fromStringWithType("-1000.123");
    test:assertEquals(floatValue2, -1000.123);

    Float floatValue3 = check fromStringWithType("2.012");
    test:assertEquals(floatValue3, 2.012);
}

type Decimal decimal;

@test:Config
isolated function testStringToDecimal() returns error? {
    decimal decimalValue1 = check fromStringWithType("2.012");
    test:assertEquals(decimalValue1, 2.012d);

    decimal decimalValue2 = check fromStringWithType("-1000.123");
    test:assertEquals(decimalValue2, -1000.123d);

    Decimal decimalValue3 = check fromStringWithType("2.012");
    test:assertEquals(decimalValue3, 2.012d);
}

type Boolean boolean;

@test:Config
isolated function testStringToBoolean() returns error? {
    boolean booleanValue1 = check fromStringWithType("true");
    test:assertTrue(booleanValue1);

    boolean booleanValue2 = check fromStringWithType("1");
    test:assertTrue(booleanValue2);

    boolean booleanValue3 = check fromStringWithType("TRUE");
    test:assertTrue(booleanValue3);

    boolean booleanValue4 = check fromStringWithType("false");
    test:assertFalse(booleanValue4);

    boolean booleanValue5 = check fromStringWithType("0");
    test:assertFalse(booleanValue5);

    boolean booleanValue6 = check fromStringWithType("FALSE");
    test:assertFalse(booleanValue6);

    Boolean booleanValue7 = check fromStringWithType("true");
    test:assertTrue(booleanValue7);
}

type Nil ();

@test:Config
isolated function testStringToNil() returns error? {
    () nilValue1 = check fromStringWithType("null");
    test:assertEquals(nilValue1, ());

    () nilValue2 = check fromStringWithType("NULL");
    test:assertEquals(nilValue2, ());

    () nilValue3 = check fromStringWithType("()");
    test:assertEquals(nilValue3, ());

    Nil nilValue4 = check fromStringWithType("null");
    test:assertEquals(nilValue4, ());
}

@test:Config
isolated function testStringToString() returns error? {
    string stringValue1 = check fromStringWithType("Hello World");
    test:assertEquals(stringValue1, "Hello World");

    string stringValue2 = check fromStringWithType("1.2");
    test:assertEquals(stringValue2, "1.2");

    string stringValue3 = check fromStringWithType("true");
    test:assertEquals(stringValue3, "true");
}

type Union1 float|decimal;

@test:Config
isolated function testStringToUnion() returns error? {
    float|decimal value1 = check fromStringWithType("1.2");
    test:assertTrue(value1 is float);
    test:assertEquals(value1, 1.2);

    decimal|float|int value2 = check fromStringWithType("1.2");
    test:assertTrue(value2 is float);
    test:assertEquals(value2, 1.2);

    float|decimal value3 = check fromStringWithType("1");
    test:assertTrue(value3 is float);
    test:assertEquals(value3, 1.0);

    decimal|float|int value4 = check fromStringWithType("1");
    test:assertTrue(value4 is int);
    test:assertEquals(value4, 1);

    ()|string value5 = check fromStringWithType("null");
    test:assertTrue(value5 is ());
    test:assertEquals(value5, ());

    ()|string value6 = check fromStringWithType("()");
    test:assertTrue(value6 is ());
    test:assertEquals(value6, ());

    boolean|string value7 = check fromStringWithType("true");
    test:assertTrue(value7 is boolean);
    test:assertEquals(value7, true);

    boolean|string value8 = check fromStringWithType("false");
    test:assertTrue(value8 is boolean);
    test:assertEquals(value8, false);

    int|boolean value9 = check fromStringWithType("1");
    test:assertTrue(value9 is int);
    test:assertEquals(value9, 1);

    float|decimal|boolean value10 = check fromStringWithType("0");
    test:assertTrue(value10 is float);
    test:assertEquals(value10, 0.0);

    decimal|boolean value11 = check fromStringWithType("0");
    test:assertTrue(value11 is decimal);
    test:assertEquals(value11, 0.0d);

    int|string value12 = check fromStringWithType("1");
    test:assertTrue(value12 is int);
    test:assertEquals(value12, 1);

    float|string value13 = check fromStringWithType("1.2");
    test:assertTrue(value13 is float);
    test:assertEquals(value13, 1.2);

    decimal|string value14 = check fromStringWithType("1.2");
    test:assertTrue(value14 is decimal);
    test:assertEquals(value14, 1.2d);

    int|string value15 = check fromStringWithType("abc");
    test:assertTrue(value15 is string);
    test:assertEquals(value15, "abc");

    int|string value16 = check fromStringWithType("9223372036854775808");
    test:assertTrue(value16 is string);
    test:assertEquals(value16, "9223372036854775808");

    int|string value17 = check fromStringWithType("-9223372036854775809");
    test:assertTrue(value17 is string);
    test:assertEquals(value17, "-9223372036854775809");

    decimal|string value18 = check fromStringWithType("9.99E+61111");
    test:assertTrue(value18 is string);
    test:assertEquals(value18, "9.99E+61111");

    decimal|string value19 = check fromStringWithType("-9.99E+61111");
    test:assertTrue(value19 is string);
    test:assertEquals(value19, "-9.99E+61111");

    Union1 value20 = check fromStringWithType("1.2");
    test:assertTrue(value20 is float);
    test:assertEquals(value20, 1.2);
}

// Negative tests for fromStringWithType() function.

@test:Config
isolated function testStringToIntNegative() {
    int|error error1 = fromStringWithType("1AF");
    test:assertTrue(error1 is error);
    test:assertEquals((<error>error1).message(), "'string' value '1AF' cannot be converted to 'int'");

    int|error error2 = fromStringWithType("1.2");
    test:assertTrue(error2 is error);
    test:assertEquals((<error>error2).message(), "'string' value '1.2' cannot be converted to 'int'");

    int|error error3 = fromStringWithType("true");
    test:assertTrue(error3 is error);
    test:assertEquals((<error>error3).message(), "'string' value 'true' cannot be converted to 'int'");

    int|error error4 = fromStringWithType("()");
    test:assertTrue(error4 is error);
    test:assertEquals((<error>error4).message(), "'string' value '()' cannot be converted to 'int'");

    int|error error5 = fromStringWithType("-9223372036854775809");
    test:assertTrue(error5 is error);
    test:assertEquals((<error>error5).message(), "'string' value '-9223372036854775809' cannot be converted to " +
    "'int'");

    int|error error6 = fromStringWithType("9223372036854775808");
    test:assertTrue(error6 is error);
    test:assertEquals((<error>error6).message(), "'string' value '9223372036854775808' cannot be converted to " +
    "'int'");
}

@test:Config
isolated function testStringToFloatNegative() {
    float|error error1 = fromStringWithType("1AF");
    test:assertTrue(error1 is error);
    test:assertEquals((<error>error1).message(), "'string' value '1AF' cannot be converted to 'float'");

    float|error error2 = fromStringWithType("true");
    test:assertTrue(error2 is error);
    test:assertEquals((<error>error2).message(), "'string' value 'true' cannot be converted to 'float'");

    float|error error3 = fromStringWithType("()");
    test:assertTrue(error3 is error);
    test:assertEquals((<error>error3).message(), "'string' value '()' cannot be converted to 'float'");
}

@test:Config
isolated function testStringToDecimalNegative() {
    decimal|error error1 = fromStringWithType("1AF");
    test:assertTrue(error1 is error);
    test:assertEquals((<error>error1).message(), "'string' value '1AF' cannot be converted to 'decimal'");

    decimal|error error2 = fromStringWithType("true");
    test:assertTrue(error2 is error);
    test:assertEquals((<error>error2).message(), "'string' value 'true' cannot be converted to 'decimal'");

    decimal|error error3 = fromStringWithType("()");
    test:assertTrue(error3 is error);
    test:assertEquals((<error>error3).message(), "'string' value '()' cannot be converted to 'decimal'");
}

@test:Config
isolated function testStringToBooleanNegative() {
    boolean|error error1 = fromStringWithType("1AF");
    test:assertTrue(error1 is error);
    test:assertEquals((<error>error1).message(), "'string' value '1AF' cannot be converted to 'boolean'");

    boolean|error error2 = fromStringWithType("1.2");
    test:assertTrue(error2 is error);
    test:assertEquals((<error>error2).message(), "'string' value '1.2' cannot be converted to 'boolean'");

    boolean|error error3 = fromStringWithType("()");
    test:assertTrue(error3 is error);
    test:assertEquals((<error>error3).message(), "'string' value '()' cannot be converted to 'boolean'");
}

@test:Config
isolated function testStringToNilNegative() {
    ()|error error1 = fromStringWithType("1AF");
    test:assertTrue(error1 is error);
    test:assertEquals((<error>error1).message(), "'string' value '1AF' cannot be converted to '()'");

    ()|error error2 = fromStringWithType("1.2");
    test:assertTrue(error2 is error);
    test:assertEquals((<error>error2).message(), "'string' value '1.2' cannot be converted to '()'");

    ()|error error3 = fromStringWithType("true");
    test:assertTrue(error3 is error);
    test:assertEquals((<error>error3).message(), "'string' value 'true' cannot be converted to '()'");

    ()|error error4 = fromStringWithType("1");
    test:assertTrue(error4 is error);
    test:assertEquals((<error>error4).message(), "'string' value '1' cannot be converted to '()'");
}

@test:Config
isolated function testStringToUnionNegative() {
    float|decimal|error error1 = fromStringWithType("1AF");
    test:assertTrue(error1 is error);
    test:assertEquals((<error>error1).message(), "'string' value '1AF' cannot be converted to '(float|decimal)'");

    ()|boolean|error error2 = fromStringWithType("1.2");
    test:assertTrue(error2 is error);
    test:assertEquals((<error>error2).message(), "'string' value '1.2' cannot be converted to 'boolean?'");

    int|float|()|error error3 = fromStringWithType("true");
    test:assertTrue(error3 is error);
    test:assertEquals((<error>error3).message(), "'string' value 'true' cannot be converted to '(int|float)?'");
}
