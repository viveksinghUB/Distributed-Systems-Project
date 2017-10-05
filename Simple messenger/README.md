SimpleMessenger
=====================

This is a simple messenger app on Android.

The goal of this app is simple: enabling two Android devices to send messages to each other.


You need to have the Android SDK and Python 2.x installed on your machine
Use the python scripts to create, run and set the ports of the AVD's, by using the following commands 

        python create_avd.py 2
        python run_avd.py 2
        python set_redir.py 10000
        
Now you can run and install the app on the AVD's and check for communication by sending messages from the AVD's

Also Note that-
        
 * In the app, you can open only one server socket that listens on port 10000 regardless of which AVD your app runs on.
        
 * The app on avd0 can connect to the listening server socket of the app on avd1 by connecting to <ip>:<port> == 10.0.2.2:11112.
        
 * The app on avd1 can connect to the listening server socket of the app on avd0 by connecting to <ip>:<port> == 10.0.2.2:11108.
        
 * The app knows which AVD it is running on via the following code snippet.
         
 * If portStr is "5554", then it is avd0. If portStr is "5556",then it is avd1:
        
                TelephonyManager tel =
                        (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
                String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

There is a tester for checking if everything is working properly.


**Note:** The python scripts and tester are provided by [Prof. Steve Ko](http://www.cse.buffalo.edu/people/?u=stevko) from the University at Buffalo.

Happy Messaging :)