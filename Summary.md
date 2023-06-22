# Ballerina Data Module

The Ballerina Data module offers a variety of functions designed to facilitate conversion between different data formats (e.g., JSON, XML, CSV, etc.). This module is equipped to handle both string data formats (e.g., JSON, XML, CSV, etc.) and Ballerina `anydata` values. 

TODO: Complete the introduction.

## Supported String Data Formats

Below are the supported string data formats, along with the corresponding base type, and the types they can be converted from and to.

| Format           | Base Type                      | Convert From                                             | Convert To                  |
| ---------------- | ------------------------------ | -------------------------------------------------------- | --------------------------- |
| JSON             | `record {\|T...;\|}` or `json` | `string`, `stream<byte[], error>`, `byte[]`, `xml`, ...  | `xml`, `json`, records, ... |
| XML              | `record {\|T...;\|}` or `xml`  | `string`, `stream<byte[], error>`, `byte[]`, `json`, ... | `json`, records ...         |
| CSV              | `record {\|T...;\|}[]`         | `string`, `stream<byte[], error>`, `byte[]`, `json`, ... | `json`, `xml`, ...          |
| YAML             | `record {\|T...;\|}`           | TBD                                                      | TBD                         |
| TOML             | `record {\|T...;\|}`           | TBD                                                      | TBD                         |
| Properties (Map) | `record {\|T...;\|}`           | TBD                                                      | TBD                         |

<u>Highlights</u>:
* Notably, all string data types, including XML, can be represented using a record type. The constraint `T` is based on the specific string data format.
* Conversion are opinionated, but can be customized using additional parameters.

## Supported Basic Types

Ballerina `anydata` values include formats such as `json`, `xml`, records, arrays, maps, and more. 

**Note**:
Initially, we will not support all `anydata` values, only a select few basic types.

---

# Design 

The core idea of our design is to streamline the conversion process between different string data formats.
 By using a canonical representation based on the Record type,
  we can represent most data types and use this representation to facilitate conversions between various data formats,
   including different `anydata` basic types.

Another crucial aspect of our design is the use of Projection to convert what the user wishes to convert.
 This level of specificity cannot be achieved with structural typing and default cloning.

```mermaid
graph LR
    A["Source `byte[]`|`string`|`stream &amp;lt;byte[], error&gt;`"] -- "fromXYWithType" --> B["Record Type (R1)"]
    B -- "Data Mapper" -.-> C["Another Record Type (Rx)"] 
    C -- "toXY" --> D["Target `byte[]`|`string`|`stream &amp;lt;byte[], error&gt;`"]
    ST["Source Type `xml`, `json`, list, mapping, etc."] -- "fromXYWithType" --> B
    C -- "toXYWithType" --> TT["Target Type `xml`, `json`, list, mapping, etc.`"]

    subgraph "Source Data Format (JSON, XML, CSV, etc.)"
    A
    end

    subgraph "Source Basic Type"
    ST
    end

    subgraph "Target Basic Type"
    TT
    end

    subgraph "Target Data Format (JSON, XML, CSV, etc.)"
    D
    end

    subgraph "Ballerina Record Based Canonical Representation"
    B
    C
    end
```

For this design, we are introducing a new set of functions and an algorithm (called Projection) for data format conversion. 

In this context, the source data format type is `S`, and the target type is `T`. To facilitate Projection, `T` must be a subtype of `P|P[]|table<P>`, where `P` is `record {|R...;|}`.

<u>Key Points:</u>

* `P` can represent both open and closed records, which remain subtypes of `record {|R...;|}`. The openness or closeness of `P` will determine the projection of the source data.
* `R` may be constrained based on the source string data format type.

To convert `T` to another string data format, we will introduce a new set of functions specific to each string data format.
## Basic Functions

### `fromStringWithType`

This function serves as the inverse of the `toString()` function and constructs a `()|boolean|string|int|decimal|float` from a string representation.

```ballerina
isolated function fromStringWithType(string, typedesc<T> t = <>) returns T|error = external;
```

* This should be a langlib function, which is part of the `ballerina/lang.value` module. 
* A numeric-string value represents a value that belongs to one of the basic types: int, float, or decimal. 
  As with literals, we need to apply a similar algorithm to determine the type of the value.
* Most basic types provide the `fromString` langlib function. The semantics of this function are the same as those. 

### Additional Functions

While the majority of necessary functions are readily available in the langlib, such as `toString()`,
 we will still require a set of unique functions to facilitate conversions between different string data formats.
  Details about these additional functions will be introduced later in this document.

## Projection Rules

Projection can be executed at two stages:

1. On functions that construct a new Ballerina value from `string`, `byte[]`, `stream<byte[], error>` values, which represent a string data format.
2. On functions that convert an existing Ballerina value to a different Ballerina value, such as `xml`, `json`, records, arrays, etc.

The projection rules are more or less the same for both cases unless otherwise specified.

### Rules

When `P` is Open:

* If the source is a String Data type:
  - If the position of the source data matches the name of the `P` field:
    - The string value at that position is converted using `fromStringWithType`.
    - This might result in a `ConversionError` if the value is invalid (e.g., due to number format errors).
  - If there's no matching field in `P`:
    - The string value is converted to the most suitable basic type using `fromStringWithType`.
    - The suitable basic type is inferred from `R`, which will generally succeed unless `R` does not include `string`.  
  - These rules are further adjusted based on the specific String Data Type. For instance:
      - XML
        - If the position is an XML element, the element name is matched against the `P` field name.
          - If the element is a text element, the value is converted using `fromStringWithType`.
          - If the element is a complex element, it will be converted to a `record {|R...;|}` following the same rules.
        - If the position is an XML attribute, the attribute name is matched against the `P` field name, and the value is converted using `fromStringWithType`.
        - If both the element and attribute don't match, the value is converted to the most suitable basic type using `fromStringWithType`.
        - If both the element and attribute match, priority is given to the element.
        - If the position is an XML comment, it will be ignored.
        - If the position is an XML processing instruction (PI), it will be ignored.
        - If the position is an XML namespace declaration, it will be ignored by default, but this behavior can be overridden with additional parameters.
        - Unspecified fields will be converted to the most suitable basic type using `fromStringWithType`.
      - JSON
        - The rules remain the same as normal. 
        - Note: JSON Objects and JSON Arrays are treated as `record {|R...;|}` and `record {|R...;|}[]` respectively.
      - CSV
        - Multiple CSV headers are supported; only the last one will be considered for the conversion.
        - If a CSV header is present (this has to be explicitly specified by the user, the default is false)
          - The header name is first matched against the `P` field name.
          - Based on this match, the conversion is performed. If there is a type mismatch, a `ConversionError` will occur.
          - Unspecified fields will be converted to the most suitable basic type using `fromStringWithType`.
        - If a CSV header is not present
          - The position of the value is matched against the `P` field name.
            In this case, field names must be numeric strings, i.e., "0", "1", "2", etc. (TODO: Check this)
            The conversion is then performed based on this match. If there is a type mismatch, a `ConversionError` will occur.
        - Unspecified fields will be converted to the most suitable basic type using `fromStringWithType`.
        - Note: These rules apply only when `P` is used. 
          For convenience, we support a list of list representation for CSV, where projection doesn't apply.

* If the source is an existing Ballerina value:
  - If it is a mapping value:
    - The source field name should match the `P` field's name.
      - Both the source and target field basic types should be the same.
      - For numeric basic types, the `NumericConvert` operation will be performed.
      - Otherwise, it will return a `ConversionError`.
    - If there's no matching field in `P`:
      - The source field is copied to the target field if the source field's basic type is compatible with `R`.
      - Otherwise, it will return a `ConversionError`.
  - For an `xml` value:
    - The rules are the same as for an XML String Data Type.
  - For a `json` value:
    - The rules are the same as for a JSON String Data Type.
  - For a list value:
    - `P[]` will be used instead of `P`.
  - For a table value:
    - To be determined (TBD).
  - Other values are not supported and will result in a `ConversionError`.

When `P` is Closed:

* If the source is a String Data type:
  - If the source data position matches the `P` field's name, the rules are the same as above.
  - If there's no matching field in `P`, the value is ignored.
  - For different String Data Formats:
    - XML:
      - The rules are the same as when `P` is Open, except that unspecified fields will be ignored.
    - JSON:
      - The rules are the same as when `P` is Open, except that unspecified fields will be ignored.
    - CSV:
      - The rules are the same as when `P` is Open, except that unspecified fields will be ignored.
* If the source is an existing Ballerina value:
  - Same as when `P` is Open, except that unspecified fields will be ignored.


## To String Data Type

A Record can be converted to a String Data Type, if it is a subtype of `P`.

### JSON



### XML


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
