package vip.youwe.sheller.ui.controller;

import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import org.json.JSONObject;
import vip.youwe.sheller.core.ShellService;
import vip.youwe.sheller.dao.ShellManager;

public class ReverseViewController {

    private ShellManager shellManager;
    @FXML
    private TextField reverseIPText;
    @FXML
    private TextField reversePortText;
    @FXML
    private RadioButton reverseTypeMeterRadio;
    @FXML
    private RadioButton reverseTypeShellRadio;
    @FXML
    private RadioButton reverseTypeColbatRadio;
    @FXML
    private Button reverseButton;
    @FXML
    private TextArea reverseHelpTextArea;
    private ShellService currentShellService;
    private JSONObject shellEntity;
    private List<Thread> workList;
    private Label statusLabel;

    public void init(ShellService shellService, List<Thread> workList, Label statusLabel) {
        this.currentShellService = shellService;
        this.shellEntity = shellService.getShellEntity();
        this.workList = workList;
        this.statusLabel = statusLabel;
        this.initReverseView();
    }

    private void initReverseView() {
        ToggleGroup radioGroup = new ToggleGroup();
        this.reverseTypeMeterRadio.setToggleGroup(radioGroup);
        this.reverseTypeShellRadio.setToggleGroup(radioGroup);
        this.reverseTypeColbatRadio.setToggleGroup(radioGroup);
        this.reverseTypeMeterRadio.setUserData("meter");
        this.reverseTypeShellRadio.setUserData("shell");
        this.reverseTypeColbatRadio.setUserData("colbat");
        this.reverseButton.setOnAction((event) -> {
            Runnable runner = () -> {
                try {
                    String targetIP = this.reverseIPText.getText();
                    String targetPort = this.reversePortText.getText();
                    RadioButton currentTypeRadio = (RadioButton) radioGroup.getSelectedToggle();
                    if (currentTypeRadio == null) {
                        Platform.runLater(() -> {
                            this.statusLabel.setText("Please select the bounce type first.");
                        });
                        return;
                    }

                    String type = currentTypeRadio.getUserData().toString();
                    JSONObject resultObj = this.currentShellService.connectBack(type, targetIP, targetPort);
                    String status = resultObj.getString("status");
                    if (status.equals("fail")) {
                        Platform.runLater(() -> {
                            String msg = resultObj.getString("msg");
                            this.statusLabel.setText("Rebound failed:" + msg);
                        });
                    } else {
                        Platform.runLater(() -> this.statusLabel.setText("The rebound is successful."));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> this.statusLabel.setText("The operation failed:" + e.getMessage()));
                }

            };
            Thread worker = new Thread(runner);
            this.workList.add(worker);
            worker.start();
        });
    }
}