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

public isolated function fromCsvStringWithType(string data, FromOptions options = {}, typedesc<Csv> typed = <>)
        returns typed|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.csv.Native"} external;

public isolated function fromCsvBytesWithType(byte[] data, FromOptions options = {},typedesc<Csv> typed = <>)
        returns typed|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.csv.Native"} external;

public isolated function fromCsvByteSteamWithType(stream<byte[],error> data, FromOptions options = {}, typedesc<Csv> typed = <>)
        returns typed|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.csv.Native"} external;

public isolated function toString(Csv csv, ToOptions options = {}) 
    returns string|ConversionError = @java:Method {'class: "io.ballerina.stdlib.data.csv.Native"} external;