// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/jballerina.java;

public type Error distinct error;

# Parse a json byte array with projection
#
# + bytes - Byte array of json
# + typed - Typedesc to be used for filtering in the projection
# + return - The given target type representation of the given JSON byte array on success,
#            else returns an `xmldata:Error`
public isolated function fromJsonByteArrayWithType(byte[] bytes, typedesc<any> typed = <>)
        returns typed|Error = @java:Method {
    'class: "io.ballerina.stdlib.data.json.JsonToJson"
} external;

