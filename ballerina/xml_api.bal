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
# + v - Source XML value 
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromXmlWithType(json v, Options options = {}, typedesc<anydata> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.xml.Native"} external;

# A Parser a json string value with projection
#
# + s - Source XML string value 
# + options - Options to be used for filtering in the projection 
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromXmlStringWithType(string s, Options options = {}, typedesc<anydata> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.xml.Native"} external;

# Parse a json byte array with projection
#
# + v - Byte array of xml
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromXmlByteArrayWithType(byte[] v, Options options = {}, typedesc<anydata> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.xml.Native"} external;

# Parse a xml byte stream with projection
#
# + v - Byte stream of json value
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `json:Error` 
public isolated function fromXmlByteStreamWithType(stream<byte[], error?> v, Options options = {}, typedesc<anydata> t = <>)
        returns t|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.xml.Native"} external;
