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

import ballerina/jballerina.java;

# A Parse a json value with projection
#
# + v - Source JSON value 
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromJsonWithType(json v, Options options = {}, typedesc<any> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.json.Native"} external;

# A Parser a json string value with projection
#
# + s - Source JSON string value 
# + options - Options to be used for filtering in the projection 
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromJsonStringWithType(string s, Options options = {}, typedesc<any> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.json.Native"} external;

# Parse a json byte array with projection
#
# + v - Byte array of json
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromJsonByteArrayWithType(byte[] v, Options options = {}, typedesc<any> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.json.Native"} external;

# Parse a json byte stream with projection
#
# + v - Byte stream of json value
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromJsonByteStreamWithType(stream<byte[], error?> v, Options options = {}, typedesc<any> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.json.Native"} external;

// ToJsonString
// `ballerina/lang.value:toJsonString` can be used to convert a JSON value to a JSON String.

public type Options record {
    typedesc<float|decimal> numericPreference = decimal;
};

# Describes Conversion Error.
public type ConversionError distinct error<record {

    # The reason for the conversion error
    string reason;

    # The line number of the conversion error
    int line;

    # The column number of the conversion error
    int column;
}>;
