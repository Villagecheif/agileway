package com.jn.agileway.ssh.client.impl.trileadssh2;

import com.jn.agileway.ssh.client.AbstractSshConnectionFactory;
import com.jn.agileway.ssh.client.SshConnection;
import com.jn.agileway.ssh.client.impl.trileadssh2.verifier.FromSsh2HostKeyVerifierAdapter;
import com.jn.agileway.ssh.client.impl.trileadssh2.verifier.KnownHostsVerifier;
import com.jn.agileway.ssh.client.utils.SshConfigs;

import java.io.File;
import java.util.List;

public class Ssh2ConnectionFactory extends AbstractSshConnectionFactory<Ssh2ConnectionConfig> {

    public Ssh2ConnectionFactory(){
        setName("trileadssh2");
    }

    @Override
    protected Class<?> getDefaultConnectionClass() {
        return Ssh2Connection.class;
    }

    @Override
    protected void postConstructConnection(SshConnection connection, Ssh2ConnectionConfig sshConfig) {
        addHostKeyVerifiers(connection, sshConfig);
    }

    private void addHostKeyVerifiers(SshConnection connection, Ssh2ConnectionConfig sshConfig) {
        List<File> paths = SshConfigs.getKnownHostsFiles(sshConfig.getKnownHostsPath());

        if (!paths.isEmpty()) {
            KnownHostsVerifier verifier = new KnownHostsVerifier(paths);
            connection.addHostKeyVerifier(new FromSsh2HostKeyVerifierAdapter(verifier));
        }
    }

    @Override
    public Ssh2ConnectionConfig newConfig() {
        return new Ssh2ConnectionConfig();
    }
}
