package vip.youwe.sheller.ui.controller;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import org.json.JSONObject;
import vip.youwe.sheller.core.ShellService;
import vip.youwe.sheller.utils.CipherUtils;
import vip.youwe.sheller.utils.Utils;

public class TunnelViewController {

    @FXML
    private Button createPortMapBtn;
    @FXML
    private Button createSocksBtn;
    @FXML
    private Label portMapListenIPLabel;
    @FXML
    private Label portMapListenPortLabel;
    @FXML
    private Label portMapDescLabel;
    @FXML
    private Label socksListenIPLabel;
    @FXML
    private Label socksListenPortLabel;
    @FXML
    private Label socksDescLabel;
    @FXML
    private TextArea tunnelLogTextarea;
    @FXML
    private RadioButton portmapVPSRadio;
    @FXML
    private RadioButton portmapHTTPRadio;
    @FXML
    private RadioButton socksVPSRadio;

    public void init(ShellService shellService, List<Thread> workList, Label statusLabel) {
        this.currentShellService = shellService;
        this.shellEntity = shellService.getShellEntity();
        this.workList = workList;
        this.statusLabel = statusLabel;
        initTunnelView();
    }

    @FXML
    private RadioButton socksHTTPRadio;
    @FXML
    private TextField portMapTargetIPText;
    @FXML
    private TextField portMapTargetPortText;
    @FXML
    private TextField portMapIPText;
    @FXML
    private TextField portMapPortText;
    @FXML
    private TextField socksIPText;
    @FXML
    private TextField socksPortText;
    private ShellService currentShellService;
    private JSONObject shellEntity;
    private List<Thread> workList;
    private Label statusLabel;
    private ProxyUtils proxyUtils;
    private ServerSocket localPortMapSocket;

    private void initTunnelView() {
        final ToggleGroup portmapTypeGroup = new ToggleGroup();
        this.portmapVPSRadio.setToggleGroup(portmapTypeGroup);
        this.portmapHTTPRadio.setToggleGroup(portmapTypeGroup);
        this.portmapVPSRadio.setUserData("remote");
        this.portmapHTTPRadio.setUserData("local");

        ToggleGroup socksTypeGroup = new ToggleGroup();
        this.socksVPSRadio.setToggleGroup(socksTypeGroup);
        this.socksHTTPRadio.setToggleGroup(socksTypeGroup);
        this.socksVPSRadio.setUserData("remote");
        this.socksHTTPRadio.setUserData("local");

        portmapTypeGroup.selectedToggleProperty().addListener((ov, oldToggle, newToggle) -> {
            if (portmapTypeGroup.getSelectedToggle() != null) {
                String portMapType = newToggle.getUserData().toString();
                if (portMapType.equals("local")) {

                    TunnelViewController.this.portMapDescLabel.setText("*Provide single port mapping based on HTTP tunnel, map the remote target intranet port to the local, suitable for the situation where the target cannot go out of the network.");
                    TunnelViewController.this.portMapListenIPLabel.setText("Local listening IP address:");
                    TunnelViewController.this.portMapListenPortLabel.setText("Local listening port:");
                    TunnelViewController.this.portMapIPText.setText("0.0.0.0");
                } else if (portMapType.equals("remote")) {

                    TunnelViewController.this.portMapDescLabel.setText("*Provide single-port mapping based on VPS transfer, and map the remote target intranet port to the VPS, and the target machine needs to be able to go out of the network.");
                    TunnelViewController.this.portMapListenIPLabel.setText("VPS listening IP address:");
                    TunnelViewController.this.portMapListenPortLabel.setText("VPS listening port:");
                    TunnelViewController.this.portMapIPText.setText("8.8.8.8");
                }
            }
        });

        this.portMapListenIPLabel.setText("VPS listening IP address:");
        this.portMapListenPortLabel.setText("VPS listening port:");
        socksTypeGroup.selectedToggleProperty().addListener((ov, oldToggle, newToggle) -> {
            if (portmapTypeGroup.getSelectedToggle() != null) {
                String portMapType = newToggle.getUserData().toString();
                if (portMapType.equals("local")) {

                    TunnelViewController.this.socksDescLabel.setText("*Provide a global socks proxy based on HTTP tunnel, open the socks proxy service of the remote target intranet to the local, suitable for the situation where the target cannot go out of the network.");
                    TunnelViewController.this.socksListenIPLabel.setText("Local listening IP address:");
                    TunnelViewController.this.socksListenPortLabel.setText("Local listening port:");
                } else if (portMapType.equals("remote")) {

                    TunnelViewController.this.socksDescLabel.setText("*Provide a global socks proxy based on VPS transfer, open the socks proxy service of the remote target intranet to the external VPS, and the target machine needs to be able to go out of the Internet.");
                    TunnelViewController.this.socksListenIPLabel.setText("VPS listening IP address:");
                    TunnelViewController.this.socksListenPortLabel.setText("VPS listening port:");
                }
            }
        });

        this.createPortMapBtn.setOnAction(event -> {
            if (this.createPortMapBtn.getText().equals("open")) {
                RadioButton currentTypeRadio = (RadioButton) portmapTypeGroup.getSelectedToggle();
                if (currentTypeRadio.getUserData().toString().equals("local")) {

                    createLocalPortMap();
                } else if (currentTypeRadio.getUserData().toString().equals("remote")) {

                    createRemotePortMap();
                }
            } else {

                RadioButton currentTypeRadio = (RadioButton) portmapTypeGroup.getSelectedToggle();
                if (currentTypeRadio.getUserData().toString().equals("local")) {

                    stoplocalPortMap();
                } else if (currentTypeRadio.getUserData().toString().equals("remote")) {

                    stopRemotePortMap();
                }
            }
        });

        this.createSocksBtn.setOnAction(event -> {
            if (this.createSocksBtn.getText().equals("open")) {
                RadioButton currentTypeRadio = (RadioButton) socksTypeGroup.getSelectedToggle();
                if (currentTypeRadio.getUserData().toString().equals("local")) {

                    createLocalSocks();
                } else if (currentTypeRadio.getUserData().toString().equals("remote")) {

                    createRemoteSocks();
                }
            } else {

                RadioButton currentTypeRadio = (RadioButton) socksTypeGroup.getSelectedToggle();
                if (currentTypeRadio.getUserData().toString().equals("local")) {

                    stopLocalSocks();
                } else if (currentTypeRadio.getUserData().toString().equals("remote")) {

                    stopRemoteSocks();
                }
            }
        });
    }

    private void createLocalPortMap() {
        this.createPortMapBtn.setText("Close");
        String targetIP = this.portMapTargetIPText.getText();
        String targetPort = this.portMapTargetPortText.getText();
        Runnable creater = () -> {

            try {
                Runnable runner = () -> {
                    try {
                        String host = this.portMapIPText.getText();
                        int port = Integer.parseInt(this.portMapPortText.getText());
                        ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
                        serverSocket.setReuseAddress(true);
                        this.localPortMapSocket = serverSocket;
                        Platform.runLater(() -> this.tunnelLogTextarea.appendText("[INFO] is listening on the local port:" + port + "\n"));

                        while (true) {
                            Socket socket = serverSocket.accept();
                            String socketHash = Utils.getMD5("" + socket.getInetAddress() + socket.getPort() + "");
                            this.currentShellService.createPortMap(targetIP, targetPort, socketHash);
                            Platform.runLater(() -> this.tunnelLogTextarea.appendText("[INFO]The tunnel was created successfully.\n"));
                            Runnable reader = () -> {
                                while (true) {
                                    if (socket != null) {
                                        try {
                                            byte[] data = this.currentShellService.readPortMapData(targetIP, targetPort,
                                                    socketHash);
                                            if (data != null) {
                                                if (data.length == 0) {
                                                    Thread.sleep(10L);
                                                    continue;
                                                }

                                                socket.getOutputStream().write(data);
                                                socket.getOutputStream().flush();
                                                continue;
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            Platform.runLater(() -> {
                                                this.tunnelLogTextarea
                                                        .appendText("[ERROR]Data reading exception:" + e.getMessage() + "\n");
                                            });
                                            continue;
                                        }
                                    }

                                    return;
                                }
                            };

                            Runnable writer = () -> {
                                while (true) {
                                    if (socket != null) {
                                        try {
                                            socket.setSoTimeout(1000);
                                            byte[] data = new byte[' '];
                                            int length = socket.getInputStream().read(data);
                                            if (length != -1) {
                                                data = Arrays.copyOfRange(data, 0, length);
                                                this.currentShellService.writePortMapData(data, targetIP, targetPort,
                                                        socketHash);
                                                continue;
                                            }
                                        } catch (SocketTimeoutException var8) {
                                            continue;
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            Platform.runLater(() -> {
                                                this.tunnelLogTextarea
                                                        .appendText("[ERROR]Data writing exception:" + e.getMessage() + "\n");
                                            });
                                        }
                                    }

                                    try {
                                        this.currentShellService.closeLocalPortMap(targetIP, targetPort);
                                        Platform.runLater(() -> {
                                            this.tunnelLogTextarea.appendText("[INFO]The tunnel was closed successfully.\n");
                                        });
                                        socket.close();
                                    } catch (Exception e) {
                                        Platform.runLater(() -> {
                                            this.tunnelLogTextarea
                                                    .appendText("[ERROR]Failed to close the tunnel:" + e.getMessage() + "\n");
                                        });
                                        e.printStackTrace();
                                    }

                                    return;
                                }
                            };

                            Thread readWorker = new Thread(reader);
                            this.workList.add(readWorker);
                            readWorker.start();
                            Thread writeWorker = new Thread(writer);
                            this.workList.add(writeWorker);
                            writeWorker.start();
                            readWorker.join();
                            writeWorker.join();
                        }
                    } catch (Exception e) {
                        ;
                    }
                };

                Thread worker = new Thread(runner);
                this.workList.add(worker);
                worker.start();
            } catch (Exception e) {
                Platform.runLater(() -> this.tunnelLogTextarea.appendText("[ERROR]Tunnel creation failed:" + e.getMessage() + "\n"));
            }
        };

        Thread worker = new Thread(creater);
        this.workList.add(worker);
        worker.start();
    }

    private void stoplocalPortMap() {
        this.createPortMapBtn.setText("Open");
        String targetIP = this.portMapTargetIPText.getText();
        String targetPort = this.portMapTargetPortText.getText();
        Runnable runner = () -> {
            try {
                this.currentShellService.closeLocalPortMap(targetIP, targetPort);
                if (this.localPortMapSocket != null && !this.localPortMapSocket.isClosed()) {

                    try {
                        this.localPortMapSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                Platform.runLater(() -> this.tunnelLogTextarea.appendText("[INFO]The local listening port is closed.\n"));

            } catch (Exception e) {
                Platform.runLater(() -> this.tunnelLogTextarea.appendText("[ERROR]Failed to close the tunnel:" + e.getMessage() + "\n"));
            }
        };

        Thread worker = new Thread(runner);
        this.workList.add(worker);
        worker.start();
    }

    private void stopRemotePortMap() {
        this.createPortMapBtn.setText("Open");
        Runnable runner = () -> {
            try {
                this.currentShellService.closeRemotePortMap();
                Platform.runLater(() -> this.tunnelLogTextarea.appendText("[INFO]The tunnel has been closed and the remote related resources have been released.\n"));

            } catch (Exception e) {
                Platform.runLater(() -> this.tunnelLogTextarea.appendText("[ERROR]Failed to close the tunnel:" + e.getMessage() + "\n"));
            }
        };

        Thread worker = new Thread(runner);
        this.workList.add(worker);
        worker.start();
    }


    private void createRemotePortMap() {
        this.createPortMapBtn.setText("Close");
        String remoteTargetIP = this.portMapTargetIPText.getText();
        String remoteTargetPort = this.portMapTargetPortText.getText();
        String remoteIP = this.portMapIPText.getText();
        String remotePort = this.portMapPortText.getText();
        Runnable runner = () -> {
            try {
                this.currentShellService.createRemotePortMap(remoteTargetIP, remoteTargetPort, remoteIP, remotePort);
                Platform.runLater(() -> this.tunnelLogTextarea.appendText("[INFO]The tunnel is successfully established, please connect to the VPS.\n"));

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> this.tunnelLogTextarea.appendText("[ERROR]Tunnel establishment failed:" + e.getMessage() + "\n"));
            }
        };

        Thread worker = new Thread(runner);
        this.workList.add(worker);
        worker.start();
    }

    private void createLocalSocks() {
        this.createSocksBtn.setText("Close");
        this.proxyUtils = new ProxyUtils();
        this.proxyUtils.start();
    }

    private void stopLocalSocks() {
        this.proxyUtils.shutdown();
        this.createSocksBtn.setText("Open");
    }

    private void createRemoteSocks() {
        this.createSocksBtn.setText("Close");
        this.proxyUtils = new ProxyUtils();
        this.proxyUtils.start();
    }

    private void stopRemoteSocks() {
        this.proxyUtils.shutdown();
        this.createSocksBtn.setText("Open");
    }

    class ProxyUtils extends Thread {
        private Thread r;
        private Thread w;
        private Thread proxy;
        private ServerSocket serverSocket;
        private int bufSize = 65535;

        private void log(String type, String log) {
            String logLine = "[" + type + "]" + log + "\n";
            Platform.runLater(() ->
                    TunnelViewController.this.tunnelLogTextarea.appendText(logLine));
        }


        public void shutdown() {
            log("INFO", "The proxy service is being shut down");

            try {
                if (this.r != null)
                    this.r.stop();
                if (this.w != null)
                    this.w.stop();
                if (this.proxy != null)
                    this.proxy.stop();
                this.serverSocket.close();
            } catch (IOException e) {
                log("ERROR", "Proxy service shutdown exception" + e.getMessage());
            }
            log("INFO", "Proxy service has stopped");
            TunnelViewController.this.createSocksBtn.setText("Open");
        }

        public void run() {
            try {
                String socksPort = TunnelViewController.this.socksPortText.getText();
                String socksIP = TunnelViewController.this.socksIPText.getText();
                this.proxy = Thread.currentThread();
                this.serverSocket = new ServerSocket(Integer.parseInt(socksPort), 50, InetAddress.getByName(socksIP));
                this.serverSocket.setReuseAddress(true);
                log("INFO", "listening port" + socksPort);
                while (true) {
                    Socket socket = this.serverSocket.accept();
                    log("INFO", "Received client connection request.");
                    (new Session(socket)).start();
                }
            } catch (IOException e) {

                log("ERROR", "Failed to monitor port:" + e.getMessage());
                return;
            }
        }

        private class Session extends Thread {
            private Socket socket;

            public Session(Socket socket) {
                this.socket = socket;
            }

            public void run() {
                try {
                    if (handleSocks(this.socket)) {

                        TunnelViewController.ProxyUtils.this.log("INFO", "Communicating...");

                        ProxyUtils.this.r = new TunnelViewController.ProxyUtils.Session.Reader();
                        ProxyUtils.this.w = new TunnelViewController.ProxyUtils.Session.Writer();
                        ProxyUtils.this.r.start();
                        ProxyUtils.this.w.start();
                        ProxyUtils.this.r.join();
                        ProxyUtils.this.w.join();
                        // TunnelViewController.ProxyUtils.this.r = new Reader();
                        // TunnelViewController.ProxyUtils.this.w = new Writer();
                        // TunnelViewController.ProxyUtils.this.r.start();
                        // TunnelViewController.ProxyUtils.this.w.start();
                        // TunnelViewController.ProxyUtils.this.r.join();
                        // TunnelViewController.ProxyUtils.this.w.join();
                    }
                } catch (Exception e) {
                    try {
                        TunnelViewController.this.currentShellService.closeProxy();
                    } catch (Exception e1) {

                        e1.printStackTrace();
                    }
                }
            }

            private boolean handleSocks(Socket socket) throws Exception {
                int ver = socket.getInputStream().read();
                if (ver == 5)
                    return parseSocks5(socket);
                if (ver == 4) {
                    return parseSocks4(socket);
                }
                return false;
            }

            private boolean parseSocks5(Socket socket) throws Exception {
                int atyp, cmd;
                DataInputStream ins = new DataInputStream(socket.getInputStream());
                DataOutputStream os = new DataOutputStream(socket.getOutputStream());
                int nmethods = ins.read();
                int methods = ins.read();
                os.write(new byte[]{5, 0});
                int version = ins.read();
                if (version == 2) {
                    version = ins.read();
                    cmd = ins.read();
                    int rsv = ins.read();
                    atyp = ins.read();
                } else {
                    cmd = ins.read();
                    int rsv = ins.read();
                    atyp = ins.read();
                }

                byte[] targetPort = new byte[2];
                String host = "";

                if (atyp == 1) {
                    byte[] target = new byte[4];
                    ins.readFully(target);
                    ins.readFully(targetPort);
                    String[] tempArray = new String[4];
                    for (int i = 0; i <target.length; i++) {
                        int temp = target[i] & 0xFF;
                        tempArray[i] = temp + "";
                    }

                    for (String temp: tempArray) {
                        host = host + temp + ".";
                    }
                    host = host.substring(0, host.length()-1);
                } else if (atyp == 3) {
                    int targetLen = ins.read();
                    byte[] target = new byte[targetLen];
                    ins.readFully(target);
                    ins.readFully(targetPort);
                    host = new String(target);
                } else if (atyp == 4) {
                    byte[] target = new byte[16];
                    ins.readFully(target);
                    ins.readFully(targetPort);
                    host = new String(target);
                }
                int port = (targetPort[0] & 0xFF) * 256 + (targetPort[1] & 0xFF);
                if (cmd == 2 || cmd == 3)
                    throw new Exception("not implemented");
                if (cmd == 1) {
                    host = InetAddress.getByName(host).getHostAddress();
                    if (TunnelViewController.this.currentShellService.openProxy(host, port + "")) {
                        os.write(CipherUtils.mergeByteArray(new byte[][]{{5, 0, 0, 1
                        }, InetAddress.getByName(host).getAddress(), targetPort}));
                        TunnelViewController.ProxyUtils.this.log("INFO", "Tunnel established successfully, request remote address" + host + ":" + port);
                        return true;
                    }
                    os.write(CipherUtils.mergeByteArray(new byte[][]{{5, 0, 0, 1
                    }, InetAddress.getByName(host).getAddress(), targetPort}));
                    throw new Exception(String.format("[%s:%d] Remote failed", host, port));
                }

                throw new Exception("Socks5-Unknown CMD");
            }

            private boolean parseSocks4(Socket socket) throws Exception {
                return false;
            }

            private class Reader extends Thread {
                private Reader() {
                }

                public void run() {
                    while (TunnelViewController.ProxyUtils.Session.this.socket != null) {

                        try {
                            byte[] data = TunnelViewController.this.currentShellService.readProxyData();

                            if (data == null)
                                break;
                            if (data.length == 0) {
                                Thread.sleep(100L);
                                continue;
                            }
                            TunnelViewController.ProxyUtils.Session.this.socket.getOutputStream().write(data);
                            TunnelViewController.ProxyUtils.Session.this.socket.getOutputStream().flush();
                        } catch (Exception e) {

                            TunnelViewController.ProxyUtils.this.log("ERROR", "Data reading exception" + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }

            private class Writer
                    extends Thread {
                private Writer() {
                }

                public void run() {
                    while (TunnelViewController.ProxyUtils.Session.this.socket != null) {

                        try {
                            TunnelViewController.ProxyUtils.Session.this.socket.setSoTimeout(1000);
                            byte[] data = new byte[TunnelViewController.ProxyUtils.this.bufSize];
                            int length = TunnelViewController.ProxyUtils.Session.this.socket.getInputStream().read(data);
                            if (length == -1)
                                break;
                            data = Arrays.copyOfRange(data, 0, length);
                            TunnelViewController.this.currentShellService.writeProxyData(data);
                        } catch (SocketTimeoutException e) {

                        } catch (Exception e) {
                            TunnelViewController.ProxyUtils.this.log("ERROR", "Data writing exception:" + e.getMessage());
                            e.printStackTrace();
                            break;
                        }
                    }
                    try {
                        TunnelViewController.this.currentShellService.closeProxy();
                        TunnelViewController.ProxyUtils.this.log("INFO", "The tunnel was closed successfully.");
                        TunnelViewController.ProxyUtils.Session.this.socket.close();
                    } catch (Exception e) {

                        TunnelViewController.ProxyUtils.this.log("ERROR", "Tunnel closure failed" + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}