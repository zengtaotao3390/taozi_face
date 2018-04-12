import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Splitter;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class Face_plus_plus_thread {

    static final String url = "http://218.206.177.147:8080/cmcc/api/rest/recog/compareByPic";
    static final String sysId = "0";
    static final String userId = "cmccwlw";
    static final String password = "cmccwlw1234";
//    static String base64Pic1 = "";
//    static String base64Pic2 = "";
    static final String face_dir = "E:\\work\\AI\\train-image\\positive_face";
    static final Logger total_log = LoggerFactory.getLogger("face_total");
    static final Logger success_log = LoggerFactory.getLogger("face_success");
    static final Logger error_log = LoggerFactory.getLogger("face_error");
    static final Logger face_detect_error = LoggerFactory.getLogger("face_detect_error");
    static final Logger face_plus_plus = LoggerFactory.getLogger(Face_plus_plus_thread.class.getClass());
    static AtomicInteger count = new AtomicInteger(0);
    static AtomicInteger error = new AtomicInteger(0);
    static AtomicInteger success = new AtomicInteger(0);
    static AtomicInteger detectError = new AtomicInteger(0);
    static float thresholds = 0.6741f;
//    static float thresholds = 73.975f;
    static int total_time_used = 0;
    static final HttpRequestHelper httpRequestHelper = new HttpRequestHelper();

    public static void main(String[] args) throws Exception {
        testFacePlusPlus();
    }

//    @Test
    public static void testFacePlusPlus() throws Exception {
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(15);
        List<String> orgList = new ArrayList<>();
        List<String> desList = new ArrayList<>();
//        万不得已采用按引用传递
        traverseFolder2(face_dir, orgList, desList);
        Set<String> oneByOneFaceSet = new HashSet<String>();
        for (final String orgFace : orgList) {
            for (final String desFace : desList) {
                long start = System.currentTimeMillis();
                String oneByOneFaces = orgFace + desFace;
                oneByOneFaceSet.add(oneByOneFaces);
                if (!orgFace.equals(desFace) && oneByOneFaceSet.add(desFace + orgFace)) {
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                oneByOneFaceRecognition(orgFace, desFace);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    threadPool.submit(runnable);
                }
                System.out.println(String.format("used time: %d", System.currentTimeMillis() - start));
            }
        }
        threadPool.shutdown();
    }

    private static void oneByOneFaceRecognition(String orgFace, String desFace) throws Exception {
        long startTime = System.currentTimeMillis();
        HttpResponse httpResponse = requestFacePlusPlus(orgFace, desFace);
        long costTime = System.currentTimeMillis() - startTime;
        if(httpResponse == null){
            return;
        }
        String returnMsg = EntityUtils.toString(httpResponse.getEntity());
        JSONObject json = JSON.parseObject(returnMsg);
        System.out.println("return Msg is " + returnMsg);
        String jsonData = json.getString("data");
        int resultCode = json.getInteger("resultCode");
        if(resultCode == -1){
            System.out.println("return Msg is " + returnMsg);
            face_detect_error.error("request error: + " + orgFace + " -- " + desFace + " is failure, resultCode: " + resultCode);
            face_detect_error.error("return Msg is " + returnMsg);
            detectError.getAndIncrement();
            return;
        }
        int retryTime =0;
        while (null == jsonData || "".equals(jsonData) || resultCode == -3) {
            face_detect_error.error("request error: + " + orgFace + " -- " + desFace + " is failure, retry: " + retryTime + ", resultCode: " + resultCode );
            face_detect_error.error("return Msg is " + returnMsg);
            System.out.println("error message: " + orgFace + " -- " + desFace + " is failure, retry: " + retryTime + ", resultCode: " + resultCode);
            System.out.println("return Msg is " + returnMsg);
            httpResponse = requestFacePlusPlus(orgFace, desFace);
            if(httpResponse == null){
                return;
            }
            returnMsg = EntityUtils.toString(httpResponse.getEntity());
            json = JSON.parseObject(returnMsg);
            jsonData = json.getString("data");
            resultCode = json.getInteger("resultCode");
            retryTime++;
            if(retryTime == 10){
                face_detect_error.error("ten retry error: " + orgFace + " -- " + desFace + " is failure, retry: " + retryTime);
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
        String orgFileName = orgDirList.get(6);
        String desFileName = desDirList.get(6);
        if (confidence == null) {
            face_detect_error.info("orgFace: {}, desFace: {}, face++ return meg: {}", orgFileName, desFileName, json);
            return;
        }
        total_log.info("orgFace: {}, desFace: {}, confidence: {}, costTime: {}, retry time: {}", orgFileName, desFileName, confidence, costTime, retryTime);
        count.getAndIncrement();
        recodeResults(confidence, orgDirList, desDirList, orgFileName, desFileName);
        System.out.println(String.format("count: %d, error: %d, success: %d, detect error: %d", count.get(), error.get(), success.get(), detectError.getAndIncrement()));

    }

    private static HttpResponse requestFacePlusPlus(String orgFace, String desFace) throws Exception {

        String base64Pic1 = encodeImgageToBase64(orgFace);
        String base64Pic2 = encodeImgageToBase64(desFace);

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
            face_plus_plus.error("orgFace: {}, desFace: {}, i: {}", orgFace, desFace, i);
            if(i == 20){
                return null;
            }
        }
        return httpResponse;
    }

    private static void recodeResults(Float confidence, List<String> orgDirList, List<String> desDirList, String orgFileName, String desFileName) {
        if (orgDirList.get(5).equals(desDirList.get(5))) {
            if (confidence > thresholds) {
                success.getAndIncrement();
                success_log.info("orgFace: {}, desFace: {}, confidence: {}", orgFileName, desFileName, confidence);
            } else {
                error.getAndIncrement();
                error_log.info("orgFace: {}, desFace: {}, confidence: {}, same one error!", orgFileName, desFileName, confidence);
            }
        } else {
            if (confidence > thresholds) {
                error.getAndIncrement();
                error_log.info("orgFace: {}, desFace: {}, confidence: {}, not same one error!", orgFileName, desFileName, confidence);
            } else {
                success.getAndIncrement();
                success_log.info("orgFace: {}, desFace: {}, confidence: {}", orgFileName, desFileName, confidence);
            }
        }
    }


    static void traverseFolder2(String path, List<String> orgList, List<String> desList) {
        File file = new File(path);
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files.length == 0) {
                System.out.println("文件夹是空的!");
            } else {
                for (File file2 : files) {
                    if (file2.isDirectory()) {
//                        System.out.println("文件夹:" + file2.getAbsolutePath());
                        traverseFolder2(file2.getAbsolutePath(), orgList, desList);
                    } else {
//                        System.out.println("文件:" + file2.getAbsolutePath());
//                        System.out.println(file2.getPath());
                        orgList.add(file2.getPath());
                        desList.add(file2.getPath());
                    }
                }
            }
        } else {
            System.out.println("文件不存在!");
        }
    }


    public  static String encodeImgageToBase64(String filepath) {// 将图片文件转化为字节数组字符串，并对其进行Base64编码处理
        File imageFile = new File(filepath);
        ByteArrayOutputStream outputStream = null;
        try {
            BufferedImage bufferedImage = ImageIO.read(imageFile);
            outputStream = new ByteArrayOutputStream();
            System.out.println(filepath);
            ImageIO.write(bufferedImage, "jpg", outputStream);
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 对字节数组Base64编码
        return new String(org.apache.commons.codec.binary.Base64.encodeBase64(outputStream.toByteArray()));

    }
}
