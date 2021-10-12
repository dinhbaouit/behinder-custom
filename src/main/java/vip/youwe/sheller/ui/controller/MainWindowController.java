package vip.youwe.sheller.ui.controller;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.json.JSONObject;
import vip.youwe.sheller.core.Constants;
import vip.youwe.sheller.core.ShellService;
import vip.youwe.sheller.dao.ShellManager;

import java.util.*;

public class MainWindowController {

    @FXML
    private GridPane mainGridPane;
    @FXML
    private TabPane mainTabPane;
    @FXML
    private WebView basicInfoView;
    @FXML
    private TextField urlText;
    @FXML
    private TextArea cmdTextArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Label connStatusLabel;
    @FXML
    private Label versionLabel;
    @FXML
    private TextArea sourceCodeTextArea;
    @FXML
    private TextArea sourceResultArea;
    @FXML
    private Button runCodeBtn;

    @FXML
    private Tab realCmdTab;
    private JSONObject shellEntity;
    private ShellService currentShellService;
    private ShellManager shellManager;
    @FXML
    private AnchorPane pluginView;
    @FXML
    private PluginViewController pluginViewController;
    @FXML
    private FileManagerViewController fileManagerViewController;
    @FXML
    private ReverseViewController reverseViewController;
    @FXML
    private DatabaseViewController databaseViewController;
    @FXML
    private RealCmdViewController realCmdViewController;
    @FXML
    private TunnelViewController tunnelViewController;
    private Map<String, String> basicInfoMap = new HashMap();
    private List<Thread> workList = new ArrayList();


    public void initialize() {
        initControls();
    }


    public List<Thread> getWorkList() {
        return this.workList;
    }

    private void initControls() {
        this.versionLabel.setText(String.format(this.versionLabel.getText(), Constants.VERSION));
        this.urlText.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                this.statusLabel.setText("Getting basic information, please wait...");
                this.connStatusLabel.setText("Connecting");
                WebEngine webengine = this.basicInfoView.getEngine();
                Runnable runner = () -> {
                    try {
                        this.doConnect();
                        JSONObject basicInfoObj = new JSONObject(this.currentShellService.getBasicInfo());
                        final String basicInfoStr = new String(Base64.decode(basicInfoObj.getString("basicInfo")), "UTF-8");
                        String driveList = new String(Base64.decode(basicInfoObj.getString("driveList")), "UTF-8").replace(":\\", ":/");
                        String currentPath = new String(Base64.decode(basicInfoObj.getString("currentPath")), "UTF-8");
                        String osInfo = new String(Base64.decode(basicInfoObj.getString("osInfo")), "UTF-8").toLowerCase();
                        this.basicInfoMap.put("basicInfo", basicInfoStr);
                        this.basicInfoMap.put("driveList", driveList);
                        this.basicInfoMap.put("currentPath", currentPath);
                        this.basicInfoMap.put("osInfo", osInfo.replace("winnt", "windows"));
                        this.shellManager.updateOsInfo(this.shellEntity.getInt("id"), osInfo);
                        Platform.runLater(() -> {
                            webengine.loadContent(basicInfoStr);
                            try {
                                MainWindowController.this.initCmdView();
                                MainWindowController.this.realCmdViewController.init(MainWindowController.this.currentShellService, MainWindowController.this.workList, MainWindowController.this.statusLabel, MainWindowController.this.basicInfoMap);
                                MainWindowController.this.initSourceCodeView();
                                MainWindowController.this.pluginViewController.init(MainWindowController.this.currentShellService, MainWindowController.this.workList, MainWindowController.this.statusLabel, MainWindowController.this.shellManager);
                                MainWindowController.this.fileManagerViewController.init(MainWindowController.this.currentShellService, MainWindowController.this.workList, MainWindowController.this.statusLabel, MainWindowController.this.basicInfoMap);
                                MainWindowController.this.reverseViewController.init(MainWindowController.this.currentShellService, MainWindowController.this.workList, MainWindowController.this.statusLabel);
                                MainWindowController.this.databaseViewController.init(MainWindowController.this.currentShellService, MainWindowController.this.workList, MainWindowController.this.statusLabel);
                                MainWindowController.this.tunnelViewController.init(MainWindowController.this.currentShellService, MainWindowController.this.workList, MainWindowController.this.statusLabel);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            MainWindowController.this.connStatusLabel.setText("Connected");
                            MainWindowController.this.statusLabel.setText("[OK]The connection is successful, and the basic information is obtained.");
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            MainWindowController.this.initCmdView();
                            e.printStackTrace();
                            MainWindowController.this.connStatusLabel.setText("Connection failed");
                            MainWindowController.this.connStatusLabel.setTextFill(Color.RED);
                            MainWindowController.this.statusLabel.setText("[ERROR]Connection failed:" + e.getMessage());
                        });
                    }
                };

                Thread workThrad = new Thread(runner);
                this.workList.add(workThrad);
                workThrad.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        this.mainTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            String tabId = newTab.getId();
            switch (tabId) {
                case "cmdTab":
                    MainWindowController.this.cmdTextArea.requestFocus();
                    MainWindowController.this.cmdTextArea.end();
                    break;
            }
        });
    }


    private void initCmdView() {
        String currentPath = this.basicInfoMap.get("currentPath");
        this.cmdTextArea.setText(currentPath + ">");
    }


    private void initSourceCodeView() {
        this.runCodeBtn.setOnAction(event ->
                runSourceCode());
    }


    private void runSourceCode() {
        this.statusLabel.setText("Executing...");
        Runnable runner = () -> {
            try {
                String result = this.currentShellService.eval(this.sourceCodeTextArea.getText());
                Platform.runLater(()->{
                    this.sourceResultArea.setText(result);
                    this.statusLabel.setText("Completed.");
                });

            } catch (Exception e) {

                e.printStackTrace();
                Platform.runLater(() -> {
                    this.statusLabel.setText("Failed to run:" + e.getMessage());
                    this.sourceResultArea.setText(e.getMessage());
                });
            }
        };


        Thread workThrad = new Thread(runner);
        this.workList.add(workThrad);
        workThrad.start();
    }


    public void onCMDKeyPressed(KeyEvent keyEvent) {
        KeyCode keyCode = keyEvent.getCode();
        int lineCount = this.cmdTextArea.getParagraphs().size();
        String lastLine = this.cmdTextArea.getParagraphs().get(lineCount-1).toString();
        if (keyCode == KeyCode.ENTER) {
            this.statusLabel.setText("[!] is executing the command, please wait...");
            int cmdStart = lastLine.indexOf(">") + 1;
            String cmd = lastLine.substring(cmdStart).trim();
            Runnable runner = () -> {
                try {
                    JSONObject resultObj = this.currentShellService.runCmd(cmd);
                    String statusText = resultObj.getString("status").equals("success")? "[+]Command executed successfully.": "[-]Command executed failed.";
                    Platform.runLater(() -> {
                        MainWindowController.this.statusLabel.setText(statusText);
                        MainWindowController.this.cmdTextArea.appendText("\n" + resultObj.getString("msg") + "\n");
                        MainWindowController.this.cmdTextArea.appendText(MainWindowController.this.basicInfoMap.get("currentPath") + ">");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> MainWindowController.this.statusLabel.setText("[-] operation failed:" + e.getMessage()));
                }
            };
            Thread workThrad = new Thread(runner);
            this.workList.add(workThrad);
            workThrad.start();
            keyEvent.consume();
        }
    }


    private void doConnect() throws Exception {
        boolean connectResult = this.currentShellService.doConnect();
    }


    private String getCurrentUserAgent() {
        int uaIndex = (new Random()).nextInt(Constants.userAgents.length-1);
        return Constants.userAgents[uaIndex];
    }


    public void init(JSONObject shellEntity, ShellManager shellManager, Map<String, Object> currentProxy) throws Exception {
        this.shellEntity = shellEntity;
        this.shellManager = shellManager;
        this.currentShellService = new ShellService(shellEntity, getCurrentUserAgent());

        // this.currentShellService.setProxy(currentProxy);
        ShellService.setProxy(currentProxy);
        this.urlText.setText(shellEntity.getString("url"));
    }


    private void initTabs() {
        if (this.shellEntity.getString("type").equals("asp")) {
            for (Tab tab: this.mainTabPane.getTabs()) {

                if (tab.getId().equals("realCmdTab") || tab.getId().equals("tunnelTab") || tab.getId().equals("reverseTab")) {
                    tab.setDisable(true);
                }
            }
        }
    }
}