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

public type Scalar string|int|decimal|boolean;


public type Value record {

};

public isolated function fromXmlStringWithType(string data, FromOptions options = {}, typedesc<record {}> typed = <>)
        returns typed|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.csv.Native"} external;

public isolated function fromXmlBytesWithType(byte[] data, FromOptions options = {},typedesc<record {}> typed = <>)
        returns typed|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.csv.Native"} external;

public isolated function fromXmlByteSteamWithType(stream<byte[],error> data, FromOptions options = {}, typedesc<record {}> typed = <>)
        returns typed|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.csv.Native"} external;

# Describes Conversion Error.
public type ConversionError distinct error<record {

    # The reason for the conversion error
    string reason;

    # The line number of the conversion error
    int line;

    # The column number of the conversion error
    int column;
}>;
