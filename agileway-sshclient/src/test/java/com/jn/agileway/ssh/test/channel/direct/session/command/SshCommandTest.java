package com.jn.agileway.ssh.test.channel.direct.session.command;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jn.langx.util.io.IOs;
import com.jn.langx.util.logging.Loggers;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

public class SshCommandTest {
    private static final Logger logger = Loggers.getLogger(SshCommandTest.class);

    public static void main(String[] args) throws JSchException, IOException {
        String password = "fjn13570";
        String username = "fangjinuo";
        String host = "192.168.1.79";
        int port = 22;

        JSch.setConfig("StrictHostKeyChecking","false");

        JSch jsch = new JSch();
        jsch.setKnownHosts("known_hosts");

        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.connect();


        executeAndDump(session, "route");
        executeAndDump(session, "ifconfig");
        executeAndDump(session, "cd ~/.java;ls -al");


        session.disconnect();
    }


    private static void executeAndDump(Session session, String command) throws JSchException, IOException {
        logger.info("\n====== start execute: {} ======", command);
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.connect();
        logger.info("isClosed: {}", channel.isClosed());
        logger.info("isConnected: {}", channel.isConnected());
        InputStream errorStream = channel.getErrStream();
        InputStream outStream = channel.getInputStream();
        String error = IOs.readAsString(errorStream);
        logger.info(error);
        String content = IOs.readAsString(outStream);
        logger.info(content);
        channel.disconnect();
        logger.info("====== finish execute: {} ======", command);
    }


}
