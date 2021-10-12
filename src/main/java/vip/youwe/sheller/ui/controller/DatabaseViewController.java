package vip.youwe.sheller.ui.controller;


import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.stage.FileChooser;
import org.json.JSONArray;
import org.json.JSONObject;
import vip.youwe.sheller.core.ShellService;
import vip.youwe.sheller.dao.ShellManager;
import vip.youwe.sheller.utils.Utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseViewController {

    private ShellManager shellManager;
    @FXML
    private ComboBox databaseTypeCombo;
    @FXML
    private TextField connStrText;
    @FXML
    private TextArea sqlText;
    @FXML
    private TreeView schemaTree;
    @FXML
    private TableView dataTable;

    public void init(ShellService shellService, List<Thread> workList, Label statusLabel) {
        this.currentShellService = shellService;
        this.shellEntity = shellService.getShellEntity();
        this.workList = workList;
        this.statusLabel = statusLabel;
        initDatabaseView();
    }

    @FXML
    private Button connectBtn;
    @FXML
    private Button executeSqlBtn;
    private ShellService currentShellService;
    private JSONObject shellEntity;
    private List<Thread> workList;
    private Label statusLabel;

    private void initDatabaseView() {
        this.schemaTree.setOnMouseClicked(event -> {
            TreeItem currentTreeItem = (TreeItem) this.schemaTree.getSelectionModel().getSelectedItem();
            if (currentTreeItem.isExpanded() == true) {
                currentTreeItem.setExpanded(false);

            } else if (event.getButton() == MouseButton.PRIMARY && !currentTreeItem.isExpanded()) {

                if (currentTreeItem.getGraphic().getUserData().toString().equals("database")) {
                    try {
                        showTables(currentTreeItem);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (currentTreeItem.getGraphic().getUserData().toString().equals("table")) {
                    try {
                        showColumns(currentTreeItem);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        this.executeSqlBtn.setOnAction(event -> {

            try {
                Map<String, String> connParams = parseConnURI(this.connStrText.getText());
                Runnable runner = () -> {
                    try {
                        String resultText = this.executeSQL(connParams, this.sqlText.getText());
                        Platform.runLater(() -> {
                            try {
                                this.fillTable(resultText);
                                this.statusLabel.setText("SQL execution was successful.");
                            } catch (Exception var3) {
                                this.statusLabel.setText("SQL execution failed:" + var3.getMessage());
                            }

                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> this.statusLabel.setText("SQL execution failed:" + e.getMessage()));
                    }

                };

                Thread worker = new Thread(runner);
                this.workList.add(worker);
                worker.start();
            } catch (Exception ex) {
                this.statusLabel.setText(ex.getMessage());
            }
        });
        initDatabaseType();
        loadContextMenu();
    }

    private void loadContextMenu() {
        loadTreeContextMenu();
        loadTableContextMenu();
    }

    private void loadTreeContextMenu() {
        ContextMenu treeContextMenu = new ContextMenu();
        MenuItem queryHeadBtn = new MenuItem("Query the first 10 items");
        treeContextMenu.getItems().add(queryHeadBtn);
        MenuItem queryAllBtn = new MenuItem("Query All");
        treeContextMenu.getItems().add(queryAllBtn);
        MenuItem exportBtn = new MenuItem("Export the current table");
        treeContextMenu.getItems().add(exportBtn);

        queryHeadBtn.setOnAction(event -> {
            TreeItem currentTreeItem = (TreeItem) this.schemaTree.getSelectionModel().getSelectedItem();
            String tableName = currentTreeItem.getValue().toString();
            String dataBaseName = currentTreeItem.getParent().getValue().toString();
            Runnable runner = () -> {
                try {
                    Map<String, String> connParams = this.parseConnURI(this.connStrText.getText());
                    String databaseType = (String) connParams.get("type");
                    String sql = null;
                    if (databaseType.equals("mysql")) {
                        sql = String.format("select * from %s.%s limit 10", dataBaseName, tableName);
                    } else if (databaseType.equals("sqlserver")) {
                        sql = String.format("select top 10 * from %s..%s", dataBaseName, tableName);
                    } else if (databaseType.equals("oracle")) {
                        sql = String.format("select * from %s where rownum<=10", tableName);
                    }

                    String resultText = this.executeSQL(connParams, sql);
                    Platform.runLater(() -> {
                        try {
                            this.fillTable(resultText);
                        } catch (Exception e) {
                            this.statusLabel.setText(e.getMessage());
                        }

                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        this.statusLabel.setText(e.getMessage());
                    });
                }

            };

            Thread worker = new Thread(runner);
            this.workList.add(worker);
            worker.start();
        });

        queryAllBtn.setOnAction(event -> {
            TreeItem currentTreeItem = (TreeItem) this.schemaTree.getSelectionModel().getSelectedItem();
            String tableName = currentTreeItem.getValue().toString();
            String dataBaseName = currentTreeItem.getParent().getValue().toString();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm");
            alert.setHeaderText("");
            alert.setContentText("Querying all records may take a long time, are you sure to query all records?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == ButtonType.OK) {
                Runnable runner = () -> {
                    try {
                        Map<String, String> connParams = this.parseConnURI(this.connStrText.getText());
                        String databaseType = connParams.get("type");
                        String sql = null;
                        if (databaseType.equals("mysql")) {
                            sql = String.format("select * from %s.%s", dataBaseName, tableName);
                        } else if (databaseType.equals("sqlserver")) {
                            sql = String.format("select * from %s..%s", dataBaseName, tableName);
                        } else if (databaseType.equals("oracle")) {
                            sql = String.format("select * from %s", tableName);
                        }

                        String resultText = this.executeSQL(connParams, sql);
                        Platform.runLater(() -> {
                            try {
                                this.fillTable(resultText);
                            } catch (Exception e) {
                                this.statusLabel.setText(e.getMessage());
                            }

                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            this.statusLabel.setText(e.getMessage());
                        });
                    }

                };

                Thread worker = new Thread(runner);
                this.workList.add(worker);
                worker.start();
            }
        });

        exportBtn.setOnAction(event -> {
            TreeItem currentTreeItem = (TreeItem) this.schemaTree.getSelectionModel().getSelectedItem();
            String tableName = currentTreeItem.getValue().toString();
            String dataBaseName = currentTreeItem.getParent().getValue().toString();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Please select the save path");
            fileChooser.setInitialFileName("export_table.csv");
            File selectedFile = fileChooser.showSaveDialog(this.schemaTree.getScene().getWindow());

            String selected = selectedFile.getAbsolutePath();
            if (selected == null || selected.equals(""))
                return;
            Runnable runner = () -> {
                try {
                    Map<String, String> connParams = this.parseConnURI(this.connStrText.getText());
                    String databaseType = connParams.get("type");
                    String sql = null;
                    if (databaseType.equals("mysql")) {
                        sql = String.format("select * from %s.%s", dataBaseName, tableName);
                    } else if (databaseType.equals("sqlserver")) {
                        sql = String.format("select * from %s..%s", dataBaseName, tableName);
                    } else if (databaseType.equals("oracle")) {
                        sql = String.format("select * from %s", tableName);
                    }

                    String resultText = this.executeSQL(connParams, sql);
                    StringBuilder rows = new StringBuilder();
                    JSONArray arr = new JSONArray(resultText);
                    String colsLine = "";
                    JSONArray cols = arr.getJSONArray(0);

                    int i;
                    for (i = 0; i <cols.length(); ++i) {
                        JSONObject colObj = cols.getJSONObject(i);
                        colsLine = colsLine + colObj.getString("name") + ",";
                    }

                    rows.append(colsLine + "\n");

                    for (i = 1; i <arr.length(); ++i) {
                        JSONArray cells = arr.getJSONArray(i);

                        for (int j = 0; j <cells.length(); ++j) {
                            rows.append(cells.get(j) + ",");
                        }

                        rows.append("\n");
                    }

                    FileOutputStream fso = new FileOutputStream(selected);
                    fso.write(rows.toString().getBytes());
                    fso.flush();
                    fso.close();
                    Platform.runLater(() -> {
                        this.statusLabel.setText("Export is complete, the file has been saved to" + selected);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        this.statusLabel.setText(e.getMessage());
                    });
                    e.printStackTrace();
                }

            };

            Thread worker = new Thread(runner);
            this.workList.add(worker);
            worker.start();
        });
        this.schemaTree.setOnContextMenuRequested(event -> {
            TreeItem currentTreeItem = (TreeItem) this.schemaTree.getSelectionModel().getSelectedItem();
            if (currentTreeItem.getGraphic().getUserData().toString().equals("table")) {
                treeContextMenu.show(this.schemaTree.getScene().getWindow(), event.getScreenX(), event.getScreenY());
            }
        });
    }


    private void loadTableContextMenu() {
        ContextMenu tableContextMenu = new ContextMenu();
        MenuItem copyCellBtn = new MenuItem("copy cell");
        tableContextMenu.getItems().add(copyCellBtn);
        MenuItem copyRowBtn = new MenuItem("Copy the entire row");
        tableContextMenu.getItems().add(copyRowBtn);
        MenuItem exportBtn = new MenuItem("Export all query results");
        tableContextMenu.getItems().add(exportBtn);


        copyCellBtn.setOnAction(event -> {
            TablePosition position = (TablePosition) this.dataTable.getSelectionModel().getSelectedCells().get(0);
            int row = position.getRow();
            int column = position.getColumn();
            String selectedValue = "";

            if (this.dataTable.getItems().size()> row && ((List) this.dataTable.getItems().get(row)).size()> column) {
                selectedValue = ((StringProperty) ((List) this.dataTable.getItems().get(row)).get(column)).getValue();
                Utils.setClipboardString(selectedValue);
            }
        });

        copyRowBtn.setOnAction(event -> {
            TablePosition position = (TablePosition) this.dataTable.getSelectionModel().getSelectedCells().get(0);
            int row = position.getRow();
            int column = position.getColumn();
            String selectedValue = "";
            int rowSize = this.dataTable.getItems().size();
            int columnSize = ((List) this.dataTable.getItems().get(row)).size();

            if (rowSize> row && columnSize> column) {
                String lineContent = "";
                for (int i = 0; i <columnSize; i++) {

                    selectedValue = ((StringProperty) ((List) this.dataTable.getItems().get(row)).get(i)).getValue();
                    lineContent = lineContent + selectedValue + "|";
                }
                Utils.setClipboardString(lineContent);
            }
        });

        exportBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Please select the save path");
            fileChooser.setInitialFileName("export_table.csv");
            File selectedFile = fileChooser.showSaveDialog(this.schemaTree.getScene().getWindow());
            String selected = selectedFile.getAbsolutePath();
            if (selected == null || selected.equals(""))
                return;
            int rowSize = this.dataTable.getItems().size();
            int columnSize = ((List) this.dataTable.getItems().get(0)).size();
            this.statusLabel.setText("Preparing data......");
            Runnable runner = () -> {
                StringBuilder sb = new StringBuilder();

                int i;
                for (i = 0; i <columnSize; ++i) {
                    TableColumn col = (TableColumn) this.dataTable.getColumns().get(i);
                    sb.append(col.getText() + ",");
                }

                sb.append("\n");

                for (i = 0; i <rowSize; ++i) {
                    for (int j = 0; j <columnSize; ++j) {
                        String cellString = ((StringProperty) ((List) this.dataTable.getItems().get(i)).get(j))
                                .getValue();
                        sb.append(cellString + ",");
                    }

                    sb.append("\n");
                }

                Platform.runLater(() -> {
                    this.statusLabel.setText("Writing to file..." + selected);
                });

                try {
                    FileOutputStream fso = new FileOutputStream(selected);
                    fso.write(sb.toString().getBytes());
                    fso.flush();
                    fso.close();
                    Platform.runLater(() -> {
                        this.statusLabel.setText("Export is complete, the file has been saved to" + selected);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        this.statusLabel.setText("Export failed:" + e.getMessage());
                    });
                }

            };

            Thread worker = new Thread(runner);
            this.workList.add(worker);
            worker.start();
        });
        this.dataTable.setContextMenu(tableContextMenu);
    }

    private void initDatabaseType() {
        ObservableList<String> typeList = FXCollections.observableArrayList("MySQL", "SQLServer", "Oracle");
        this.databaseTypeCombo.setItems(typeList);
        this.databaseTypeCombo.setOnAction(event -> {
            String type = this.databaseTypeCombo.getValue().toString();
            String connStr = formatConnectString(type);
            this.connStrText.setText(connStr);
        });

        this.connectBtn.setOnAction(event -> {
            try {
                showDatabases(this.connStrText.getText());
            } catch (Exception e) {
                e.printStackTrace();
                this.statusLabel.setText("Connection failed:" + e.getMessage());
            }
        });
    }


    private String formatConnectString(String type) {
        //todo's hard-coded njmubios2012 database user name
        String result = "%s://%s:njmubios2012@127.0.0.1:%s/%s";
        switch (type) {
            case "MySQL":
                result = String.format(result, "mysql", "root", "3306", "mysql");
                break;
            case "SQLServer":
                result = String.format(result, "sqlserver", "sa", "1433", "master");
                break;
            case "Oracle":
                result = String.format(result, "oracle", "sys", "1521", "orcl");
                break;
        }
        return result;
    }

    private void showTables(TreeItem currentTreeItem) throws Exception {
        Map<String, String> connParams = parseConnURI(this.connStrText.getText());
        String sql = null;
        String databaseName = currentTreeItem.getValue().toString();
        String databaseType = connParams.get("type");
        if (databaseType.equals("mysql")) {
            sql = String.format("select table_name,a.* from information_schema.tables as a where table_schema='%s' and table_type='base table'", new Object[]{databaseName});

        } else if (databaseType.equals("sqlserver")) {
            sql = String.format("select name,* from %s..sysobjects where xtype='U'", databaseName);
        } else if (databaseType.equals("oracle")) {
            sql = "select table_name,num_rows from user_tables";
        }
        String finalSql = sql;
        Runnable runner = () -> {
            try {
                String resultText = executeSQL(connParams, finalSql);
                Platform.runLater(() -> {
                    try {
                        this.fillTable(resultText);
                        this.fillTree(resultText, currentTreeItem);
                    } catch (Exception e) {
                        this.statusLabel.setText(e.getMessage());
                    }

                });


            } catch (Exception e) {
                this.statusLabel.setText(e.getMessage());
            }
        };
        Thread worker = new Thread(runner);
        this.workList.add(worker);
        worker.start();
    }


    private void showColumns(TreeItem currentTreeItem) throws Exception {
        try {
            String tableName = currentTreeItem.getValue().toString();
            String databaseName = currentTreeItem.getParent().getValue().toString();

            Map<String, String> connParams = parseConnURI(this.connStrText.getText());
            String sql = null;
            String databaseType = connParams.get("type");
            if (databaseType.equals("mysql")) {
                sql = String.format("select COLUMN_NAME,a.* from information_schema.columns as a where table_schema='%s' and table_name='%s'", new Object[]{databaseName, tableName});

            } else if (databaseType.equals("sqlserver")) {
                sql = String.format("SELECT Name,* FROM %s..SysColumns WHERE id=Object_Id('%s')", databaseName, tableName);
            } else if (databaseType.equals("oracle")) {
                sql = String.format("select COLUMN_NAME,a.* from user_tab_columns a where Table_Name='%s' ", tableName);
            }
            String resultText = executeSQL(connParams, sql);
            fillTable(resultText);
            fillTree(resultText, currentTreeItem);
        } catch (Exception ex) {
            this.statusLabel.setText(ex.getMessage());
        }
    }


    private void showDatabases(String connString) throws Exception {
        TreeItem<String> rootItem = new TreeItem<String>("Database List", new ImageView());
        rootItem.getGraphic().setUserData("root");
        this.schemaTree.setRoot(rootItem);
        this.schemaTree.setShowRoot(false);
        String shellType = this.currentShellService.getShellEntity().getString("type");
        Map<String, String> connParams = parseConnURI(connString);
        String databaseType = (connParams.get("type")).toLowerCase();
        String sql = null;
        if (databaseType.equals("mysql")) {
            sql = "show databases";
        } else if (databaseType.equals("sqlserver")) {
            sql = "SELECT name FROM master..sysdatabases";
        } else if (databaseType.equals("oracle")) {

            sql = "select sys_context('userenv','db_name') as db_name from dual";
        }
        String finalSql = sql;
        Runnable runner = () -> {
            try {
                if (shellType.equals("aspx")) {

                    loadDriver("aspx", "mysql");
                    loadDriver("aspx", "oracle");
                }
                String resultText = executeSQL(connParams, finalSql);
                if (resultText.equals("NoDriver")) {
                    loadDriver(shellType, connParams.get("type"));
                    return;
                }
                Platform.runLater(() -> {
                    try {
                        this.fillTable(resultText);
                        this.fillTree(resultText, rootItem);
                    } catch (Exception e) {
                        e.printStackTrace();
                        this.statusLabel.setText(e.getMessage());
                    }

                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    this.statusLabel.setText(e.getMessage());
                });
            }
        };

        Thread worker = new Thread(runner);
        this.workList.add(worker);
        worker.start();
    }

    private Map<String, String> parseConnURI(String url) throws Exception {
        Map<String, String> connParams = new HashMap<String, String>();
        URI connUrl = new URI(url);
        String type = connUrl.getScheme();
        String host = connUrl.getHost();
        String port = connUrl.getPort() + "";
        String authority = connUrl.getUserInfo();
        String user = authority.substring(0, authority.indexOf(":"));
        String pass = authority.substring(authority.indexOf(":") + 1);
        String database = connUrl.getPath().replaceFirst("/", "");
        String coding = "UTF-8";
        if (connUrl.getQuery() != null && connUrl.getQuery().indexOf("coding=") >= 0) {
            coding = connUrl.getQuery();
            Pattern p = Pattern.compile("([a-zA-Z]*)=([a-zA-Z0-9\\-]*)");
            Matcher m = p.matcher(connUrl.getQuery());
            while (m.find()) {
                String key = m.group(1).toLowerCase();
                if (key.equals("coding"))
                    coding = m.group(2).trim();
            }
        }
        connParams.put("type", type);
        connParams.put("host", host);
        connParams.put("port", port);
        connParams.put("user", user);
        connParams.put("pass", pass);
        connParams.put("database", database);
        connParams.put("coding", coding);
        return connParams;
    }


    private void loadDriver(String scriptType, String databaseType) throws Exception {

        // todo database driver file path, but no
        String driverPath = "net/rebeyond/behinder/resource/driver/";
        Platform.runLater(() ->
                this.statusLabel.setText("Uploading database driver......"));

        String os = this.currentShellService.shellEntity.getString("os").toLowerCase();

        String remoteDir = (os.indexOf("windows") >= 0)? "C:/windows/temp/": "/tmp/";
        String libName = null;
        if (scriptType.equals("jsp")) {
            if (databaseType.equals("sqlserver")) {
                libName = "sqljdbc41.jar";
            } else if (databaseType.equals("mysql")) {
                libName = "mysql-connector-java-5.1.36.jar";
            } else if (databaseType.equals("oracle")) {
                libName = "ojdbc5.jar";
            }
        } else if (scriptType.equals("aspx")) {
            if (databaseType.equals("mysql")) {
                libName = "mysql.data.dll";
            } else if (databaseType.equals("oracle")) {
                libName = "Oracle.ManagedDataAccess.dll";
            }
        }

        byte[] driverFileContent = Utils.getResourceData(driverPath + libName);
        String remotePath = remoteDir + libName;
        this.currentShellService.uploadFile(remotePath, driverFileContent, true);

        Platform.runLater(() ->
                this.statusLabel.setText("The driver is uploaded successfully, the driver is being loaded..."));

        JSONObject loadRes = this.currentShellService.loadJar(remotePath);
        if (loadRes.getString("status").equals("fail")) {
            throw new Exception("Drive loading failed:" + loadRes.getString("msg"));
        }

        Platform.runLater(() -> {
            if (scriptType.equals("jsp"))
                this.statusLabel.setText("The driver is loaded successfully, please click `Connect` again.");
            this.statusLabel.setText("Driver loaded successfully.");
        });
    }


    private String executeSQL(Map<String, String> connParams, String sql) throws Exception {
        Platform.runLater(() -> {
            this.statusLabel.setText("Querying, please wait...");
            this.sqlText.setText(sql);
        });
        String type = connParams.get("type");
        String host = connParams.get("host");
        String port = connParams.get("port");
        String user = connParams.get("user");
        String pass = connParams.get("pass");
        String database = connParams.get("database");
        JSONObject resultObj = this.currentShellService.execSQL(type, host, port, user, pass, database, sql);
        String status = resultObj.getString("status");
        String msg = resultObj.getString("msg");

        Platform.runLater(() -> {
            if (status.equals("success")) {
                this.statusLabel.setText("The query is complete.");
            } else if (status.equals("fail") && !msg.equals("NoDriver")) {
                this.statusLabel.setText("Query failed:" + msg);
            }
        });
        return msg;
    }


    private void fillTree(String resultText, TreeItem currentTreeItem) throws Exception {
        currentTreeItem.getChildren().clear();
        JSONArray result = new JSONArray(resultText);
        int childNums = result.length()-1;

        String childIconPath = "", childType = "";
        switch (currentTreeItem.getGraphic().getUserData().toString()) {
            case "root":
                childIconPath = "database.png";
                childType = "database";
                break;
            case "database":
                childIconPath = "database_table.png";
                childType = "table";
                break;
            case "table":
                childIconPath = "database_column.png";
                childType = "column";
                break;
            default:
        }

        Image icon = new Image(new ByteArrayInputStream(Utils.getResourceData(childIconPath)));

        for (int i = 1; i <= childNums; i++) {
            JSONArray row = result.getJSONArray(i);
            String childName = row.get(0).toString();
            TreeItem<String> treeItem = new TreeItem<String>(childName, new ImageView(icon));
            treeItem.getGraphic().setUserData(childType);
            treeItem.setValue(childName);
            currentTreeItem.getChildren().add(treeItem);
        }
        currentTreeItem.setExpanded(true);
    }


    private void fillTable(String resultText) throws Exception {
        JSONArray result;
        try {
            result = new JSONArray(resultText);
        } catch (Exception e) {
            throw new Exception(resultText);
        }
        if (!result.get(0).getClass().toString().equals("class org.json.JSONArray"))
            return;
        JSONArray fieldArray = result.getJSONArray(0);
        int rows = result.length()-1;

        ObservableList<TableColumn> tableViewColumns = FXCollections.observableArrayList();
        for (Object field: fieldArray) {
            String fieldName = ((JSONObject) field).get("name").toString();
            TableColumn<List<StringProperty>, String> col = new TableColumn<List<StringProperty>, String>(fieldName);
            tableViewColumns.add(col);
            col.setCellValueFactory(data -> (StringProperty) ((List) data.getValue()).get(0));
        }
        this.dataTable.getColumns().setAll(tableViewColumns);

        ObservableList<List<StringProperty>> data = FXCollections.observableArrayList();

        for (int i = 1; i <rows; i++) {
            JSONArray rowArr = result.getJSONArray(i);
            List<StringProperty> row = new ArrayList<StringProperty>();
            for (int j = 0; j <rowArr.length(); j++) {
                row.add(j, new SimpleStringProperty(rowArr.get(j).toString()));
            }
            data.add(row);
        }

        this.dataTable.setItems(data);
    }
}