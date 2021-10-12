package vip.youwe.sheller.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.json.JSONObject;
import vip.youwe.sheller.core.Constants;
import vip.youwe.sheller.core.ShellService;
import vip.youwe.sheller.dao.ShellManager;

import java.util.List;
import java.util.Map;

public class RealCmdViewController {

    private ShellManager shellManager;
    @FXML
    private TextArea realCmdTextArea;
    @FXML
    private TextField shellPathText;
    @FXML
    private Button realCmdBtn;
    private ShellService currentShellService;
    private JSONObject shellEntity;
    Map<String, String> basicInfoMap;
    private List<Thread> workList;
    private Label statusLabel;
    private int running;
    private int currentPos;

    public void init(ShellService shellService, List<Thread> workList, Label statusLabel, Map<String, String> basicInfoMap) {
        this.currentShellService = shellService;
        this.shellEntity = shellService.getShellEntity();
        this.basicInfoMap = basicInfoMap;
        this.workList = workList;
        this.statusLabel = statusLabel;
        initRealCmdView();
    }

    private void initRealCmdView() {
        String osInfo = this.basicInfoMap.get("osInfo");
        if (osInfo.indexOf("windows") >= 0 || osInfo.indexOf("winnt") >= 0) {

            this.shellPathText.setText("cmd.exe");
        } else {
            this.shellPathText.setText("/bin/bash");
        }
        this.realCmdBtn.setOnAction(event -> {
            if (this.realCmdBtn.getText().equals("Start")) {
                createRealCmd();
            } else {
                stopRealCmd();
            }
        });
    }

    @FXML
    private void createRealCmd() {
        this.statusLabel.setText("Starting the virtual terminal......");
        Runnable runner = () -> {
            try {
                String bashPath = this.shellPathText.getText();

                (new Thread(() -> {
                    try {
                        RealCmdViewController.this.currentShellService.createRealCMD(bashPath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })).start();

                Thread.sleep(1000L);

                JSONObject resultObj = this.currentShellService.readRealCMD();
                while (resultObj.getString("status").equals("success") && resultObj.getString("msg").equals("")) {
                    resultObj = this.currentShellService.readRealCMD();
                    Thread.sleep(1000L);
                }

                String status = resultObj.getString("status");
                String msg = resultObj.getString("msg");
                Platform.runLater(() -> {
                    if (status.equals("success")) {
                        this.realCmdTextArea.appendText(msg);
                        this.statusLabel.setText("The virtual terminal has been started.");
                        this.realCmdTextArea.requestFocus();
                        this.currentPos = this.realCmdTextArea.getLength();
                        this.realCmdBtn.setText("Stop");
                        this.running = Constants.REALCMD_RUNNING;
                    } else {
                        this.statusLabel.setText("Virtual terminal failed to start:" + msg);
                    }

                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> this.statusLabel.setText("Virtual terminal failed to start:" + e.getMessage()));
            }
        };

        Thread workThrad = new Thread(runner);
        this.workList.add(workThrad);
        workThrad.start();
    }

    private void stopRealCmd() {
        this.statusLabel.setText("Stopping the virtual terminal......");
        Runnable runner = () -> {
            try {
                JSONObject resultObj = this.currentShellService.stopRealCMD();
                String status = resultObj.getString("status");
                String msg = resultObj.getString("msg");
                Platform.runLater(() -> {
                    if (status.equals("success")) {
                        this.statusLabel.setText("The virtual terminal has stopped.");
                        this.realCmdBtn.setText("Start");
                        this.running = Constants.REALCMD_STOPPED;
                    } else {
                        this.statusLabel.setText("Virtual terminal failed to start:" + msg);
                    }

                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> this.statusLabel.setText("The operation failed:" + e.getMessage()));
            }
        };

        Thread workThrad = new Thread(runner);
        this.workList.add(workThrad);
        workThrad.start();
    }

    @FXML
    private void onRealCMDKeyPressed(KeyEvent keyEvent) {
        if (this.running != Constants.REALCMD_RUNNING) {
            this.statusLabel.setText("The virtual terminal has not been started, please start the virtual terminal first.");
            return;
        }
        if (this.realCmdTextArea.getCaretPosition() <= this.currentPos) {
            if (keyEvent.getCode() == KeyCode.ENTER) {
                this.realCmdTextArea.end();
            } else {
                keyEvent.consume();
                return;
            }
        }
        if (keyEvent.getCode() != KeyCode.ENTER) {
            return;
        }

        String cmd = this.realCmdTextArea.getText(this.currentPos, this.realCmdTextArea.getLength()).trim();

        this.statusLabel.setText("Please wait...");
        Runnable runner = () -> {
            try {
                String result = "";

                if (keyEvent.getCode() == KeyCode.ENTER) {
                    keyEvent.consume();
                    this.currentShellService.writeRealCMD(cmd + "\n");
                    Thread.sleep(1000L);
                    JSONObject resultObj = this.currentShellService.readRealCMD();

                    String status = resultObj.getString("status");
                    String msg = resultObj.getString("msg");
                    result = msg;
                    if (result.length()> 1) {
                        if (result.startsWith(cmd, 0)) {
                            result = result.substring(cmd.length());
                        }

                        result = result.startsWith("\n")? result: ("\n" + result);

                        result = result.startsWith("\n")? result.substring(1): result;
                        String finalResult = result;
                        Platform.runLater(() -> {
                            this.realCmdTextArea.appendText(finalResult);
                            this.currentPos = this.realCmdTextArea.getLength();
                        });
                        Thread.sleep(1000L);
                    }
                    Platform.runLater(() -> this.statusLabel.setText("Done."));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Thread workThrad = new Thread(runner);
        this.workList.add(workThrad);
        workThrad.start();
        keyEvent.consume();
    }
}