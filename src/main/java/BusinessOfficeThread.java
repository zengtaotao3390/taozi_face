import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Splitter;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Administrator on 2018/4/12 0012.
 */
public class BusinessOfficeThread {

    static final String FACE_DIR = "D:\\java\\taozi\\maoyan";
    static final String url = "http://218.206.177.147:8080/cmcc/api/rest/recog/compareByPic";
    static final String sysId = "0";
    static final String userId = "cmccwlw";
    static final String password = "cmccwlw1234";
    static final Logger total_log = LoggerFactory.getLogger("face_total");
    static final Logger success_log = LoggerFactory.getLogger("face_success");
    static final Logger error_log = LoggerFactory.getLogger("face_error");
    static final Logger face_detect_error = LoggerFactory.getLogger("face_detect_error");
    static final Logger business_office = LoggerFactory.getLogger(BusinessOfficeThread.class.getClass());
    static AtomicInteger count = new AtomicInteger(0);
    static AtomicInteger error = new AtomicInteger(0);
    static AtomicInteger success = new AtomicInteger(0);
    static AtomicInteger detectError = new AtomicInteger(0);
    static float thresholds = 0.6741f;
    static final HttpRequestHelper httpRequestHelper = new HttpRequestHelper();

    public static void main(String[] args) throws Exception {
        readFile("D:\\java\\中移物联网\\猫眼\\猫眼提取照片v1.0\\猫眼提取照片v1.0\\pair_mismatch.txt");
    }

    public static void readFile(String filePath) throws Exception {
        List<String> orgPaths = new ArrayList<>();
        List<String> desPaths = new ArrayList<>();
        int total1 = 0;
        try {
            FileReader reader = new FileReader(filePath);
            BufferedReader br = new BufferedReader(reader);
            String str = null;

            while((str = br.readLine()) != null) {
                total1++;
                String[] array = str.split(" ");
                traverseFolder(orgPaths,desPaths,array[0],array[2]);
            }
            br.close();
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("orgPaths.size = "+orgPaths.size() + " desPaths.size = " + desPaths.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(50);
        for (int i = 0; i < orgPaths.size(); i++) {
            int index = i;
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        oneByOneFaceRecognition(orgPaths.get(index),desPaths.get(index));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            threadPool.submit(runnable);
        }
        threadPool.shutdown();
    }

    //找到文件所在位置
    public static void traverseFolder(List<String> orgs, List<String> deses, String org, String des) {
        File file = new File(FACE_DIR);
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files.length == 0) {
                System.out.println("文件夹为空");
            } else {
                for (File file2 : files) {
                    File desFile = null;
                    File orgFile = null;
                    if (file2.isDirectory()) {//文件夹
                        orgFile = new File(file2,org);
                        if (orgFile.exists()) {
                            for (File file1 : files) {
                                if (file1.isDirectory()) {//文件夹
                                    desFile = new File(file1, des);
                                    if (desFile.exists()) {
                                        orgs.add(orgFile.getAbsolutePath());
                                        deses.add(desFile.getAbsolutePath());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void oneByOneFaceRecognition(String orgFace, String desFace) throws Exception {
        long startTime = System.currentTimeMillis();
        HttpResponse httpResponse = requestBusinessOffice(orgFace, desFace);
        long costTime = System.currentTimeMillis() - startTime;
        if(httpResponse == null){
            return;
        }
        String returnMsg = EntityUtils.toString(httpResponse.getEntity());
        JSONObject json = JSON.parseObject(returnMsg);
        String jsonData = json.getString("data");
        int resultCode = json.getInteger("resultCode");
        if(resultCode == -1){
            detectError.getAndIncrement();
            System.out.println("return Msg is " + returnMsg);
            face_detect_error.error(orgFace + " and " + desFace + " is not detected;resultCode: " + resultCode);
            face_detect_error.error("return Msg is "+  returnMsg);
            return;
        }
        int retryTime =0;
        while (null == jsonData || "".equals(jsonData) || resultCode == -3) {
            face_detect_error.error(orgFace + " and " + desFace + " is not detected, retry: " + retryTime + " ;resultCode: " + resultCode);
            face_detect_error.error("return Msg is "+  returnMsg);
//            System.out.println("error message: " + orgFace + " -- " + desFace + " is failure, retry: " + retryTime + ", resultCode: " + resultCode);
//            System.out.println("return Msg is " + returnMsg);
            httpResponse = requestBusinessOffice(orgFace, desFace);
            if(httpResponse == null){
                return;
            }
            returnMsg = EntityUtils.toString(httpResponse.getEntity());
            json = JSON.parseObject(returnMsg);
            jsonData = json.getString("data");
            resultCode = json.getInteger("resultCode");
            retryTime++;
            if(retryTime == 10){
                detectError.getAndIncrement();
                face_detect_error.error("ten retry error: " + orgFace + " and " + desFace + " is not detected, retry: " + retryTime);
                return;
            }
        }
        JSONObject jsonObject = JSON.parseObject(jsonData);
        Float confidence = jsonObject.getFloat("avg");
        thresholds = jsonObject.getFloat("threshold");
        List<String> orgDirList = Splitter.on("\\").trimResults().
                omitEmptyStrings().splitToList(orgFace);
        List<String> desDirList = Splitter.on("\\").trimResults().
                omitEmptyStrings().splitToList(desFace);
        String orgFileName = orgDirList.get(5);
        String desFileName = desDirList.get(5);
        if (confidence == null) {
            face_detect_error.info("{} and {} ,{}", orgFileName, desFileName, json);
            return;
        }
        count.getAndIncrement();
        recodeResults(confidence, orgDirList, desDirList, orgFileName, desFileName);
        total_log.info("{} matches {} similarity is confidence: {} ", orgFileName, desFileName, confidence);
        total_log.info("Circle {} spent {} s", count.get(),(double)costTime/1000);
        total_log.info("count_right: {}",success.get());
        total_log.info("count_wrong:{}",error.get());
        DecimalFormat decimalFormat = new DecimalFormat(".00");
        total_log.info("accuracy {}%",decimalFormat.format((double)success.get()*100/count.get()));
        System.out.println(String.format("count: %d, error: %d, success: %d, detect error: %d", count.get(), error.get(), success.get(), detectError.get()));

    }

    private static HttpResponse requestBusinessOffice(String orgFace, String desFace) throws Exception {

        String base64Pic1 = Face_plus_plus_thread.encodeImgageToBase64(orgFace);
        String base64Pic2 = Face_plus_plus_thread.encodeImgageToBase64(desFace);

        Map<String, Object> params = new HashMap<>();
        params.put("sysId", sysId);
        params.put("userId", userId);
        params.put("password",password);
        params.put("base64Pic1", base64Pic1);
        params.put("base64Pic2", base64Pic2);
        params.put("file1", orgFace);
        params.put("file2", desFace);
        HttpResponse httpResponse = httpRequestHelper.execPostRequest(params, url, new HashMap<String, String>());
        int i = 0;
        while (httpResponse.getStatusLine().getStatusCode() != 200) {
            httpResponse = httpRequestHelper.execPostRequest(params, url, new HashMap<String, String>());
            i++;
            business_office.error("{} and {}  http request failure, i: {}", orgFace, desFace, i);
            if(i == 20){
                return null;
            }
        }
        return httpResponse;
    }

    private static void recodeResults(Float confidence, List<String> orgDirList, List<String> desDirList, String orgFileName, String desFileName) {
        if (orgDirList.get(4).equals(desDirList.get(4))) {
            if (confidence > thresholds) {
                success.getAndIncrement();
                success_log.info("{} matches {} similarity is {}", orgFileName, desFileName, confidence);
            } else {
                error.getAndIncrement();
                error_log.info("{} dismatches {} similarity is {}", orgFileName, desFileName, confidence);
            }
        } else {
            if (confidence > thresholds) {
                error.getAndIncrement();
                error_log.info("{} matches {} similarity is {}", orgFileName, desFileName, confidence);
            } else {
                success.getAndIncrement();
                success_log.info("{} dismatches {} similarity is {} ", orgFileName, desFileName, confidence);
            }
        }
    }

}
