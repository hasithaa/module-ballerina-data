# Data Type

## Supported String Data formats. 

Following are the supported string data formats and the corresponding base type, convert to and from types.

| Format           | Base Type                      | Convert From                                     | Convert To     |
| ---------------- | ------------------------------ | ------------------------------------------------ | -------------- |
| JSON             | `record {\|T...;\|}` or `json` | string, stream<byte[], error>, byte[], xml, csv  | xml, csv, ...  |
| XML              | `record {\|T...;\|}` or `xml`  | string, stream<byte[], error>, byte[], json, csv | json, csv, ... |
| CSV              | `record {\|T...;\|}[]`         | string, stream<byte[], error>, byte[], json      | json, xml, ... |
| YAML             | `record {\|T...;\|}`           | TBD                                              | TBD            |
| TOML             | `record {\|T...;\|}`           | TBD                                              | TBD            |
| Properties (Map) | `record {\|T...;\|}`           | TBD                                              | TBD            |

As you can see all string data types (including xml) can be represented using a `record` type.
`T` is constrained based on the string data format.

# Design 

## Basic function

### `fromStringWithType`

`isolated function fromStringWithType(string, typedesc<T> t = <>) returns T|error`

This is inverse of `toString()` function, which does construct a `()|boolean|string|int|decimal|float` from a string representation.

* This must be a langlib function, which is part of the `ballerina/lang.value` module. 
* A numeric-string value represents a value belonging to one of the basic types int, float or decimal. 
  Like literals, we need to apply similar algorithm to determine the type of the value.
* Most basic type, provides `fromString` langlib function. Semantics of this function is same as those. 


## Projection Rules

Projection can be done at two points. 

1. On Functions that Constructs a new Ballerina value from a `string`, `bye[]`, `stream<byte[], error>` values, which represents a String Data format.
1. On Function that Converts existing Ballerina value to a Different Ballerina Value. such as `xml`, `json`, records, arrays, etc.

Projection rules are more or less same for both cases, unless otherwise specified.

Here Source Data format type is `S` and Target type is `T`. To Support projection, 
`T` must be a subtype of `P|P[]|table<P>`, where `P` is `record {|R...;|}`. 

Note:
* Still `P` can be both open and closed records, which is still a subtype of `record {|R...;|}`.
 Openness of `P` will be used to determine the projection rules.
* `R` is may be constrained based on the Source String Data format type.

### Rules

When `P` is Open

* If the source is a String Data type.
  - If the source data position matches to the `P` field's name
    - The string value in that position is converted using `fromStringWithType`.
    - This may return a `ConversionError` if the value is invalid. i.e. Number format errors.
  - Else (No matching field in `P`), 
    - The string value is converted to most suitable basic type using `fromStringWithType`.
    - Suitable basic type is inferred from the `R`, which will succeed most of the time, unless the `R` does not include `string`.  

* If the source is existing Ballerina value.
  - If it is a mapping value
    - If source field name should match to the `P` field's name.
      - Both source and target field basic types should be same.
      - For numeric basic types, `NumericConvert` operation will be performed.
      - Otherwise, it will return a `ConversionError`.
    - Else (No matching field in `P`), 
      - The source field is copy to the target field, if the source filed basic type compatible with the `R`.
      - Else, it will return a `ConversionError`.
  - If it is a xml value
    - TBD
  - Other values are not supported and will return a `ConversionError`.

When `P` is Closed

* If the source is a String Data type.
  - If the source data position matches to the `P` field's name
    - Same as above.
  - Else (No matching field in `P`), 
    - Ignore the value.
* If the source is existing Ballerina value.
  - If it is a mapping value
    - If source field name should match to the `P` field's name.
      - Same as above.
    - Else (No matching field in `P`), 
      - Ignore the value.
  - If it is a xml value
    - TBD
  - Other values are not supported and will return a `ConversionError`.


## Existing Conversions

Here are some of the functions I found in the Ballerina language specification that start with "from" and "to" and perform some type of conversion:

### From

| Category               | Module                 | Function Signature                                         | Description                                                     |
| ---------------------- | ---------------------- | ---------------------------------------------------------- | --------------------------------------------------------------- |
| XX                     | ballerina/lang.array   | `fromBase64(string) returns byte[]\|error`                 | Converts a Base64 string to a byte array                        |
| XX                     | ballerina/lang.array   | `fromBase16(string) returns byte[]\|error`                 | Converts a Base16 string to a byte array                        |
| `String`               | ballerina/lang.boolean | `fromString(string) returns T\|error`                      | Construct value from a string representation.                   |
| `String`               | ballerina/lang.decimal | `fromString(string) returns T\|error`                      | Construct value from a string representation.                   |
| `String`               | ballerina/lang.float   | `fromString(string) returns T\|error`                      | Construct value from a string representation.                   |
| `String`               | ballerina/lang.int     | `fromString(string) returns T\|error`                      | Construct value from a string representation.                   |
| `String`               | ballerina/lang.regex   | `fromString(string) returns T\|error`                      | Construct value from a string representation.                   |
| `String`               | ballerina/lang.xml     | `fromString(string) returns T\|error`                      | Construct value from a string representation.                   |
| XX`String`             | ballerina/lang.float   | `fromHexString(string) returns float\|error`               | Converts a hexadecimal string representation to a float value   |
| XX`String`             | ballerina/lang.int     | `fromHexString(string) returns int\|error`                 | Converts a hexadecimal string representation to a decimal value |
| XX`String`             | ballerina/lang.value   | `fromBalString(string) returns anydata\|error`             | -                                                               |
| XX`String`             | ballerina/lang.value   | `fromJsonString(string) returns json\|error`               | -                                                               |
| XX`String`             | ballerina/lang.value   | `fromJsonFloatString(string) returns JsonFloat\|error`     | -                                                               |
| XX`String`             | ballerina/lang.value   | `fromJsonDecimalString(string) returns JsonDecimal\|error` | -                                                               |
| String Data `WithType` | ballerina/lang.value   | `fromJsonStringWithType(string, T) returns T\|error`       | -                                                               |
| DATA `WithType`        | ballerina/lang.value   | `fromJsonWithType(json, T) returns T\|error`               | -                                                               |
| DATA                   | ballerina/lang.float   | `fromBitsInt(int) returns float`                           | -                                                               |
| DATA                   | ballerina/lang.string  | `fromBytes(byte[]) returns string\|error`                  | -                                                               |
| DATA                   | ballerina/lang.string  | `fromCodePointInts(int[]) returns string\|error`           | -                                                               |
| DATA                   | ballerina/lang.string  | `fromCodePointInt(int) returns Char\|error`                | -                                                               |

### TO

| Category         | Module                | Function Signature                                           | Description                                                              |
| ---------------- | --------------------- | ------------------------------------------------------------ | ------------------------------------------------------------------------ |
| Type             | ballerina/lang.array  | `toStream(T[] arr) returns stream<T,()>`                     | Converts an array to a stream                                            |
| Type             | ballerina/lang.float  | `toBitsInt(float x) returns int`                             | IEEE 64-bit binary floating point format representation of `x` as an int |
| Type             | ballerina/lang.map    | `toArray(map<Type> m) returns Type[]`                        | Returns a list of all the members of a map                               |
| Type             | ballerina/lang.string | `toBytes(string str) returns byte[]`                         | Represents `str` as an array of bytes using UTF-8                        |
| Type             | ballerina/lang.string | `toCodePointInts(string str) returns int[]`                  | Converts a string to an array of code points                             |
| Type             | ballerina/lang.string | `toCodePointInt(Char ch) returns int`                        | Converts a single character string to a code point                       |
| Type             | ballerina/lang.table  | `toArray(table<MapType> t) returns MapType[]`                | Returns a list of all the members of a table                             |
| Type             | ballerina/lang.value  | `toJson(anydata v) returns json`                             | Converts a value of type `anydata` to `json`                             |
| StringDataFormat | ballerina/lang.array  | `toBase64(byte[] arr) returns string`                        | Converts a byte array to a Base64 string                                 |
| StringDataFormat | ballerina/lang.array  | `toBase16(byte[] arr) returns string`                        | Converts a byte array to a Base16 string                                 |
| StringDataFormat | ballerina/lang.float  | `toHexString(float x) returns string`                        | Converts a float value to a hexadecimal string representation            |
| StringDataFormat | ballerina/lang.float  | `toFixedString(float x, int? fractionDigits) returns string` | Returns a string that represents `x` using fixed-point notation          |
| StringDataFormat | ballerina/lang.float  | `toExpString(float x, int? fractionDigits) returns string`   | Returns a string that represents `x` using scientific notation           |
| StringDataFormat | ballerina/lang.int    | `toHexString(int n) returns string`                          | Returns representation of `n` as hexadecimal string                      |
| StringDataFormat | ballerina/int.string  | `toLowerAscii(string str) returns string`                    | Converts occurrences of A-Z to a-z                                       |
| StringDataFormat | ballerina/int.string  | `toUpperAscii(string str) returns string`                    | Converts occurrences of a-z to A-Z                                       |
| StringDataFormat | ballerina/lang.value  | `toJsonString(anydata v) returns string`                     | Returns the string that represents `v` in JSON format                    |
| String           | ballerina/lang.value  | `toString(any v) returns string`                             | -                                                                        |
| String           | ballerina/lang.error  | `toString(error v) returns string`                           | -                                                                        |
| BalString        | ballerina/lang.value  | `toBalString(any v) returns string`                          | -                                                                        |
| BalString        | ballerina/lang.error  | `toBalString(error v) returns string`                        | -                                                                        |
