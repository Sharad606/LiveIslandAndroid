package com.praveen.liveisland;
interface IPrivilegedShell {
    String exec(String command);
    void destroy();
}
