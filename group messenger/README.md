GroupMessenger
==============

Totally and Causally Ordered Group Messenger with a Local Persistent Key-Value Table

This application builds on the [simple messenger app](https://github.com/viveksinghUB/Distributed-Systems-Project/tree/master/Simple%20messenger).
This is a group messenger application that preserves total ordering as well as causal ordering of all messages.
In addition, it implements a key-value table that each device uses to individually store all messages on its
local storage.

The provider has two columns.
    
1.	The first column is “key”. This column is used to store all keys.
2.	The second column is “value”. This column is used to store all values.
3.	String datatype is used for storing all keys and values.

Only two methods insert() and query() are implemented currently.
Since the column names are “key” and “value”, any app should be able to 
insert a <key, value> pair as in the following example:

    ContentValues keyValueToInsert = new ContentValues();
    // inserting <”key-to-insert”, “value-to-insert”>
    keyValueToInsert.put(“key”, “key-to-insert”);
    keyValueToInsert.put(“value”, “value-to-insert”);
    Uri newUri = getContentResolver().insert(
        providerUri,    // assume we already created a Uri object with our provider URI
        keyValueToInsert
    );

If there’s a new value inserted using an existing key, we keep only the most recent value.
History of values under the same key is not preserved, its overwritten.

Similarly, any app can read a <key, value> pair from the provider with query().

Provider can answer queries as in the following example:

    Cursor resultCursor = getContentResolver().query(
        providerUri,    // assume we already created a Uri object with our provider URI
        null,                // no need to support the projection parameter
        “key-to-read”,    // we provide the key directly as the selection parameter
        null,                // no need to support the selectionArgs parameter
        null                 // no need to support the sortOrder parameter
    );

The app multicasts every user-entered message to all app instances (including the one that is sending the message). 
The app uses B-multicast.
The algorithm in the application provides a total-causal ordering.

The app opens one server socket that listens on 10000.
Use the python scripts to create, run and set the ports of the AVD’s, by using the following commands 

    python create_avd.py 5
    python run_avd.py 5
    python set_redir.py 10000

The redirection ports for the AVD’s will be-

    emulator-5554: “5554” - 11108
    emulator-5556: “5556” - 11112
    emulator-5558: “5558” - 11116
    emulator-5560: “5560” - 11120
    emulator-5562: “5562” - 11124

Every message is stored in the provider individually by all app instances.
Each message should is stored as a <key, value> pair.

The key is the final delivery sequence number for the message (as a string);
the value should is the actual message (again, as a string).

The delivery sequence number starts from 0 and increase by 1 for each message.
 
There is a tester for checking if everything is working properly.


**Note:** The python scripts and tester are provided by [Prof. Steve Ko](http://www.cse.buffalo.edu/people/?u=stevko) from the University at Buffalo.