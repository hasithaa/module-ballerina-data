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

// CSV DataSet Representation

# Represents a CSV data set as a collection of Rows
public type DataSet Row[];

# Represent a CSV row.
#
# Each Row is a collection of Cells.
public type Row Cell[];

# Represent a CSV cell.
#
# A Cell can have different types of data: string, int, float, decimal, boolean, or null.
# decimal is used to represent floating point numbers by default.
#
# Empty fields are often interpreted as `()` or an empty string `""` 
# depending on the configuration passed.
#
public type Cell string|int|float|decimal|boolean|();

// Optional Representation

# Represent a CSV row mapping including the headers
public type RowMapping map<Cell>;

# Represent a CSV DataSet including the headers
public type MappingDataSet RowMapping[];

# Represent a CSV DataSet including the headers as a table
public type TableDataSet table<RowMapping>;

# Represent a any CSV DataSet representation
public type Csv DataSet|MappingDataSet|TableDataSet;


type Options record {|
    # The encoding of the CSV file
    string encoding = "utf-8";

    # The delimiter used to separate fields
    string delimiter = ","; // or string:char ?

    # Indicates CVS values are quoted.
    boolean quotedString = true;

    # Indicates Empty fields are interpreted as null or empty string
    # 
    # When Converting from:
    # If `quotedString` is `true`, then any empty field is interpreted as nil, without considering this option.
    # If `quotedString` is `false` and `emptyAsNil` is `true`, then any empty field is interpreted as nil
    # Otherwise, any empty field is interpreted as an empty string.
    # 
    # When Converting to:
    # If `quotedString` is `true`, then any nil value is converted to an empty field, without considering this option.
    # If emptyAsNil is `true`, then any empty string "" is converted an empty field.
    # Otherwise, any empty string "" is converted to "".
    boolean emptyAsNil = true;
|};

# CSV parse Options
public type FromOptions record { 
    // TODO : Need to find a better name. ReadOptions is a better alternative,
    //        But doesn't works will with ToOptions.

    *Options;

    # Trim leading space in a field
    boolean trimLeadingSpace = true;

    # Trim trailing space in a field
    boolean trimTrailingSpace = true;

    # Header details
    # 
    # If `()`, consider without headers
    # If `headers` are provided, then read headers from the given row.
    # If `headerRows` > 1, Then all row between 1 and (`headerRows` - 1) are considered as other headers and will be ignored.
    record {|
        # The number of header rows
        int:Unsigned32 headerRows = 1;
    |}? headers = ();
};

public type ToOptions record { 
    // TODO : Need to find a better name. Saying WriteOptions is not correct.
    
    *Options;

    # The line separator to use
    string lineSeparator = "\n";

    # The Suffix to use after the delimiter. 
    # Some cases adding a suffix such as a space is required to improve readability.
    string delimiterSuffix = "";
    
    # Header details
    # 
    # If `()`, continue without headers
    # If `true`, then write headers from Csv DataSet.
    #   This is possible only if the DataSet is a MappingDataSet or TableDataSet.
    #   Otherwise, ConversionError is returned.
    # Otherwise, then write headers from give values, before writing the actual data.
    #   If length mismatch between headers, then ConversionError is returned.
    #   Headers from MappingDataSet or TableDataSet are ignored in this case.
    record {|
        string[][] headers;
    |}|true? headers = ();
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
