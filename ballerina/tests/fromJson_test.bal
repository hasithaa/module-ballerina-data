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

// Possitive tests for fromJsonWithType() function.

@test:Config
isolated function testJsonToBasicTypes() returns error? {
    int val1 = check fromJsonWithType(5);
    test:assertEquals(val1, 5);

    float val2 = check fromJsonWithType(5.5);
    test:assertEquals(val2, 5.5);

    decimal val3 = check fromJsonWithType(5.5);
    test:assertEquals(val3, 5.5d);

    string val4 = check fromJsonWithType("hello");
    test:assertEquals(val4, "hello");

    boolean val5 = check fromJsonWithType(true);
    test:assertEquals(val5, true);

    () val6 = check fromJsonWithType(null);
    test:assertEquals(val6, null);

    json jsonContent = {
        "a": "hello",
        "b": 1,
        "c": {
            "d": "world",
            "e": 2
        }
    };

    string val7 = check fromJsonWithType(jsonContent);
    test:assertEquals(val7, "{\"a\":\"hello\",\"b\":1,\"c\":{\"d\":\"world\",\"e\":2}}");
}

@test:Config
isolated function testSimpleJsonToRecord() returns error? {
    json j = {"a": "hello", "b": 1};

    record {|string a; int b;|} recA = check fromJsonWithType(j);
    test:assertEquals(recA.a, "hello");
    test:assertEquals(recA.b, 1);
}

@test:Config
isolated function testSimpleJsonToRecordWithProjection() returns error? {
    json j = {"a": "hello", "b": 1};

    record {|string a;|} recA = check fromJsonWithType(j);
    test:assertEquals(recA.a, "hello");
    test:assertEquals(recA, {"a": "hello"});
}

@test:Config
isolated function testNestedJsonToRecord() returns error? {
    json j = {
        "a": "hello",
        "b": 1,
        "c": {
            "d": "world",
            "e": 2
        }
    };

    record {|string a; int b; record {|string d; int e;|} c;|} recA = check fromJsonWithType(j);
    test:assertEquals(recA.a, "hello");
    test:assertEquals(recA.b, 1);
    test:assertEquals(recA.c.d, "world");
    test:assertEquals(recA.c.e, 2);
}

@test:Config
isolated function testNestedJsonToRecordWithProjection() returns error? {
    json j = {
        "a": "hello",
        "b": 1,
        "c": {
            "d": "world",
            "e": 2
        }
    };

    record {|string a; record {|string d;|} c;|} recA = check fromJsonWithType(j);
    test:assertEquals(recA.a, "hello");
    test:assertEquals(recA.c.d, "world");
    test:assertEquals(recA, {"a": "hello", "c": {"d": "world"}});
}

@test:Config
isolated function testJsonToRecordWithOptionalFields() returns error? {
    json j = {"a": "hello"};

    record {|string a; int b?;|} recA = check fromJsonWithType(j);
    test:assertEquals(recA.a, "hello");
    test:assertEquals(recA.b, null);
}

@test:Config
isolated function testJsonToRecordWithOptionalFieldsWithProjection() returns error? {
    json j = {
        "a": "hello",
        "b": 1,
        "c": {
            "d": "world",
            "e": 2
        }
    };

    record {|string a; record {|string d; int f?;|} c;|} recA = check fromJsonWithType(j);
    test:assertEquals(recA.a, "hello");
    test:assertEquals(recA.c.d, "world");
    test:assertEquals(recA, {"a": "hello", "c": {"d": "world"}});
}

type Address record {
    string street;
    string city;
};

type R record {|
    int id;
    string name;
    Address address;
|};

@test:Config
isolated function testFromJsonWithType1() returns error? {
    json jsonContent = {
        "id": 2,
        "name": "Anne",
        "address": {
            "street": "Main",
            "city": "94"
        }
    };

    R x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.id, 2);
    test:assertEquals(x.name, "Anne");
    test:assertEquals(x.address.street, "Main");
    test:assertEquals(x.address.city, "94");
}

type Company record {
    map<string> employees;
};

@test:Config
isolated function testMapTypeAsFieldTypeInRecord() returns error? {
    json jsonContent = {
        "employees": {
            "John": "Manager",
            "Anne": "Developer"
        }
    };

    Company x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.employees["John"], "Manager");
    test:assertEquals(x.employees["Anne"], "Developer");
}

type Coordinates record {
    float latitude;
    float longitude;
};

type AddressWithCord record {
    string street;
    int zipcode;
    Coordinates coordinates;
};

type Person record {
    string name;
    int age;
    AddressWithCord address;
};

@test:Config
isolated function testFromJsonWithType2() returns error? {
    json jsonContent = {
        "name": "John",
        "age": 30,
        "address": {
            "street": "123 Main St",
            "zipcode": 10001,
            "coordinates": {
                "latitude": 40.7128,
                "longitude": -74.0060
            }
        }
    };

    Person x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.name, "John");
    test:assertEquals(x.age, 30);
    test:assertEquals(x.address.street, "123 Main St");
    test:assertEquals(x.address.zipcode, 10001);
    test:assertEquals(x.address.coordinates.latitude, 40.7128);
    test:assertEquals(x.address.coordinates.longitude, -74.0060);
}

type Author record {|
    string name;
    string birthdate;
    string hometown;
    boolean...;
|};

type Publisher record {|
    string name;
    int year;
    string...;
|};

type Book record {|
    string title;
    Author author;
    Publisher publisher;
    float...;
|};

@test:Config
isolated function testFromJsonWithType3() returns error? {
    json jsonContent = {
        "title": "To Kill a Mockingbird",
        "author": {
            "name": "Harper Lee",
            "birthdate": "1926-04-28",
            "hometown": "Monroeville, Alabama",
            "local": false
        },
        "price": 10.5,
        "publisher": {
            "name": "J. B. Lippincott & Co.",
            "year": 1960,
            "location": "Philadelphia",
            "month": 4
        }
    };

    Book x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.title, "To Kill a Mockingbird");
    test:assertEquals(x.author.name, "Harper Lee");
    test:assertEquals(x.author.birthdate, "1926-04-28");
    test:assertEquals(x.author.hometown, "Monroeville, Alabama");
    test:assertEquals(x.publisher.name, "J. B. Lippincott & Co.");
    test:assertEquals(x.publisher.year, 1960);
    test:assertEquals(x.publisher["location"], "Philadelphia");
    test:assertEquals(x["price"], 10.5);
    test:assertEquals(x.author["local"], false);
}

type School record {|
    string name;
    int number;
    boolean flag;
    int...;
|};

@test:Config
isolated function testFromJsonWithType4() returns error? {
    json jsonContent = {
        "name": "School Twelve",
        "city": 23,
        "number": 12,
        "section": 2,
        "flag": true,
        "tp": 12345
    };

    School x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.name, "School Twelve");
    test:assertEquals(x.number, 12);
    test:assertEquals(x.flag, true);
    test:assertEquals(x["section"], 2);
    test:assertEquals(x["tp"], 12345);
}

type TestRecord record {
    int intValue;
    float floatValue;
    string stringValue;
    decimal decimalValue;
};

@test:Config
function testFromJsonWithType5() returns error? {
    json jsonContent = {
        "intValue": 10,
        "floatValue": 10.5,
        "stringValue": "test",
        "decimalValue": 10.50,
        "doNotParse": "abc"
    };

    TestRecord x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.intValue, 10);
    test:assertEquals(x.floatValue, 10.5f);
    test:assertEquals(x.stringValue, "test");
    test:assertEquals(x.decimalValue, 10.50d);
    test:assertEquals(x["doNotParse"], "abc");
}

type SchoolAddress record {
    string street;
    string city;
};

type School1 record {
    string name;
    SchoolAddress address;
};

type Student1 record {
    int id;
    string name;
    School1 school;
};

type Teacher record {
    int id;
    string name;
};

type Class record {
    int id;
    string name;
    Student1 student;
    Teacher teacher;
    Student1? monitor;
};

@test:Config
function testFromJsonWithType6() returns error? {
    json jsonContent = {
        "id": 1,
        "name": "Class A",
        "student": {
            "id": 2,
            "name": "John Doe",
            "school": {
                "name": "ABC School",
                "address": {
                    "street": "Main St",
                    "city": "New York"
                }
            }
        },
        "teacher": {
            "id": 3,
            "name": "Jane Smith"
        },
        "monitor": null
    };

    Class x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.id, 1);
    test:assertEquals(x.name, "Class A");
    test:assertEquals(x.student.id, 2);
    test:assertEquals(x.student.name, "John Doe");
    test:assertEquals(x.student.school.name, "ABC School");
    test:assertEquals(x.student.school.address.street, "Main St");
    test:assertEquals(x.student.school.address.city, "New York");
    test:assertEquals(x.teacher.id, 3);
    test:assertEquals(x.teacher.name, "Jane Smith");
    test:assertEquals(x.monitor, null);
}

type TestRecord2 record {
    int intValue;
    TestRecord nested1;
};

@test:Config
function testFromJsonWithType7() returns error? {
    json nestedJson = {
        "intValue": 5,
        "floatValue": 2.5,
        "stringValue": "nested",
        "decimalValue": 5.00
    };

    json jsonContent = {
        "intValue": 10,
        "nested1": nestedJson
    };

    TestRecord2 x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.intValue, 10);
    test:assertEquals(x.nested1.intValue, 5);
}

type TestR record {|
    string street;
    string city;
|};

@test:Config
isolated function testFromJsonWithType8() returns error? {
    json jsonContent = {
        "street": "Main",
        "city": "Mahar",
        "house": 94
    };

    TestR x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.street, "Main");
    test:assertEquals(x.city, "Mahar");
}

type TestArr1 record {
    string street;
    string city;
    int[] houses;
};

@test:Config
isolated function testFromJsonWithType9() returns error? {
    json jsonContent = {
        "street": "Main",
        "city": "Mahar",
        "houses": [94, 95, 96]
    };

    TestArr1 x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.street, "Main");
    test:assertEquals(x.city, "Mahar");
    test:assertEquals(x.houses, [94, 95, 96]);
}

type TestArr2 record {
    string street;
    int city;
    [int, string] house;
};

@test:Config
isolated function testFromJsonWithType10() returns error? {
    json jsonContent = {
        "street": "Main",
        "city": 11,
        "house": [94, "Gedara"]
    };

    TestArr2 x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.street, "Main");
    test:assertEquals(x.city, 11);
    test:assertEquals(x.house, [94, "Gedara"]);
}

type TestArr3 record {
    string street;
    string city;
    [int, int[3]] house;
};

@test:Config
isolated function testFromJsonWithType11() returns error? {
    json jsonContent = {
        "street": "Main",
        "city": "Mahar",
        "house": [94, [1, 2, 3]]
    };

    TestArr3 x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.street, "Main");
    test:assertEquals(x.city, "Mahar");
    test:assertEquals(x.house, [94, [1, 2, 3]]);
}

type TestJson record {
    string street;
    json city;
    boolean flag;
};

@test:Config
isolated function testFromJsonWithType12() returns error? {
    json jsonContent = {
        "street": "Main",
        "city": {
            "name": "Mahar",
            "code": 94
        },
        "flag": true
    };

    TestJson x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.street, "Main");
    test:assertEquals(x.city, {"name": "Mahar", "code": 94});
}

@test:Config
isolated function testFromJsonWithType13() returns error? {
    json jsonContent = {
        "street": "Main",
        "city": "Mahar",
        "house": [94, [1, 3, "4"]]
    };

    TestArr3|error x = fromJsonWithType(jsonContent);
    test:assertTrue(x is error);
}

@test:Config
isolated function testFromJsonWithType14() returns error? {
    json jsonContent = {
        "id": 12,
        "name": "Anne",
        "address": {
            "id": 34,
            "city": "94"
        }
    };

    RN|ConversionError x = fromJsonWithType(jsonContent);
    test:assertTrue(x is error);
    test:assertEquals((<error>x).message(), "required field 'street' not present in JSON");
}

type intArr int[];

@test:Config
isolated function testFromJsonWithType15() returns error? {
    json jsonContent = [1, 2, 3];

    intArr x = check fromJsonWithType(jsonContent);
    test:assertEquals(x, [1, 2, 3]);
}

type tup [int, string, [int, float]];

@test:Config
isolated function testFromJsonWithType16() returns error? {
    json jsonContent = [1, "abc", [3, 4.0]];

    tup|ConversionError x = check fromJsonWithType(jsonContent);
    test:assertEquals(x, [1, "abc", [3, 4.0]]);
}

@test:Config
isolated function testFromJsonWithType17() returns error? {
    json jsonContent = {
        "street": "Main",
        "city": {
            "name": "Mahar",
            "code": 94,
            "internal": {
                "id": 12,
                "agent": "Anne"
            }
        },
        "flag": true
    };

    TestJson x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.street, "Main");
    test:assertEquals(x.city, {"name": "Mahar", "code": 94, "internal": {"id": 12, "agent": "Anne"}});
}

type BookA record {|
    string title;
    string author;
|};

type Library record {
    BookA[2] books;
};

@test:Config
isolated function testFromJsonWithType18() returns error? {
    json jsonContent = {
        "books": [
            {
                "title": "The Great Gatsby",
                "author": "F. Scott Fitzgerald"
            },
            {
                "title": "The Grapes of Wrath",
                "author": "John Steinbeck"
            },
            {
                "title": "Binary Echoes: Unraveling the Digital Web",
                "author": "Alexandra Quinn"
            }
        ]
    };

    Library x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.books.length(), 2);
    test:assertEquals(x.books[0].title, "The Great Gatsby");
    test:assertEquals(x.books[0].author, "F. Scott Fitzgerald");
    test:assertEquals(x.books[1].title, "The Grapes of Wrath");
    test:assertEquals(x.books[1].author, "John Steinbeck");
}

type LibraryB record {
    [BookA, BookA] books;
};

type LibraryC record {|
    [BookA, BookA...] books;
|};

@test:Config
isolated function testFromJsonWithType19() returns error? {
    json jsonContent = {
        "books": [
            {
                "title": "The Great Gatsby",
                "author": "F. Scott Fitzgerald"
            },
            {
                "title": "The Grapes of Wrath",
                "author": "John Steinbeck"
            },
            {
                "title": "Binary Echoes: Unraveling the Digital Web",
                "author": "Alexandra Quinn"
            }
        ]
    };

    LibraryB x = check fromJsonWithType(jsonContent);
    test:assertEquals(x.books.length(), 2);
    test:assertEquals(x.books[0].title, "The Great Gatsby");
    test:assertEquals(x.books[0].author, "F. Scott Fitzgerald");
    test:assertEquals(x.books[1].title, "The Grapes of Wrath");
    test:assertEquals(x.books[1].author, "John Steinbeck");

    LibraryC y = check fromJsonWithType(jsonContent);
    test:assertEquals(y.books.length(), 3);
    test:assertEquals(y.books[0].title, "The Great Gatsby");
    test:assertEquals(y.books[0].author, "F. Scott Fitzgerald");
    test:assertEquals(y.books[1].title, "The Grapes of Wrath");
    test:assertEquals(y.books[1].author, "John Steinbeck");
    test:assertEquals(y.books[2].title, "Binary Echoes: Unraveling the Digital Web");
    test:assertEquals(y.books[2].author, "Alexandra Quinn");
}

// Negative tests for fromJsonWithType() function.

type AddressN record {
    string street;
    string city;
    int id;
};

type RN record {|
    int id;
    string name;
    AddressN address;
|};

@test:Config
isolated function testFromJsonWithTypeNegative1() returns error? {
    json jsonContent = {
        "id": 12,
        "name": "Anne",
        "address": {
            "street": "Main",
            "city": "94",
            "id": true
        }
    };

    RN|ConversionError x = fromJsonWithType(jsonContent);
    test:assertTrue(x is error);
    test:assertEquals((<error>x).message(), "incompatible value 'true' for type 'int' in field 'address.id");
}

type RN2 record {|
    int id;
    string name;
|};

@test:Config
isolated function testFromJsonWithTypeNegative2() returns error? {
    json jsonContent = {
        "id": 12
    };

    RN2|ConversionError x = fromJsonWithType(jsonContent);
    test:assertTrue(x is error);
    test:assertEquals((<error>x).message(), "required field 'name' not present in JSON");
}

@test:Config
isolated function testFromJsonWithTypeNegative3() returns error? {
    json jsonContent = {
        "id": 12,
        "name": "Anne",
        "address": {
            "street": "Main",
            "city": "94"
        }
    };

    RN|ConversionError x = fromJsonWithType(jsonContent);
    test:assertTrue(x is error);
    test:assertEquals((<error>x).message(), "required field 'id' not present in JSON");
}

type Union int|float;

@test:Config
isolated function testFromJsonWithTypeNegative4() returns error? {
    json jsonContent = {
        name: "John"
    };

    int|ConversionError x = fromJsonWithType(jsonContent);
    test:assertTrue(x is error);
    test:assertEquals((<error>x).message(), "incompatible type for json: int");

    Union|ConversionError y = fromJsonWithType(jsonContent);
    test:assertTrue(y is error);
    test:assertEquals((<error>y).message(), "incompatible type for json: (int|float)");

    table<RN2>|ConversionError z = fromJsonWithType(jsonContent);
    test:assertTrue(z is error);
    test:assertEquals((<error>z).message(), "incompatible type for json: table<data:RN2>");

    RN2|ConversionError a = fromJsonWithType("1");
    test:assertTrue(a is error);
    test:assertEquals((<error>a).message(), "incompatible type for json: data:RN2");
}

type INTARR int[3];
type INTTUPLE [int, int, int, int...];

@test:Config
isolated function testFromJsonWithTypeNegative5() returns error? {
    json jsonContent = [1, 2];

    INTARR|ConversionError x = fromJsonWithType(jsonContent);
    test:assertTrue(x is error);
    test:assertEquals((<error>x).message(), "size mismatch between target and source");

    INTTUPLE|ConversionError y = fromJsonWithType(jsonContent);
    test:assertTrue(y is error);
    test:assertEquals((<error>y).message(), "size mismatch between target and source");
}
