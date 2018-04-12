import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Splitter;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

public class face_plus_plus {

    String url = "http://218.206.177.147:8080/cmcc/api/rest/recog/compareByPic";
    String sysId = "0";
    String userId = "cmccwlw";
    String password = "cmccwlw1234";
    String base64Pic1 = "";
    String base64Pic2 = "";
    String face_dir = "D:/java/taozi/train-image/face";
    Logger total_log = LoggerFactory.getLogger("face_total");
    Logger success_log = LoggerFactory.getLogger("face_success");
    Logger error_log = LoggerFactory.getLogger("face_error");
    Logger face_detect_error = LoggerFactory.getLogger("face_detect_error");
    int count = 0;
    int error = 0;
    int success = 0;
    static float thresholds = 0.6741f;
    int total_time_used = 0;
    HttpRequestHelper httpRequestHelper = new HttpRequestHelper();

    @Test
    public void testFacePlusPlus() throws Exception {
        List<String> orgList = new ArrayList<>();
        List<String> desList = new ArrayList<>();
//        万不得已采用按引用传递
        traverseFolder2(face_dir, orgList, desList);

        Set<String> oneByOneFaceSet = new HashSet<String>();
        for (String orgFace : orgList) {
            for (String desFace : orgList) {
                long start = System.currentTimeMillis();
                String oneByOneFaces = orgFace + desFace;
                oneByOneFaceSet.add(oneByOneFaces);
                if (!orgFace.equals(desFace) && oneByOneFaceSet.add(desFace + orgFace)) {
                    oneByOneFaceRecognition(orgFace, desFace);
                }
                System.out.println(String.format("used time: %d", System.currentTimeMillis() - start));
            }
        }

    }

    private void oneByOneFaceRecognition(String orgFace, String desFace) throws Exception {

        base64Pic1 = encodeImgageToBase64(orgFace);
        base64Pic2 = encodeImgageToBase64(desFace);

        Map<String, Object> params = new HashMap<>();
        params.put("sysId", sysId);
        params.put("userId", userId);
        params.put("password",password);
        params.put("base64Pic1", base64Pic1);
        params.put("base64Pic2", base64Pic2);
        HttpResponse httpResponse = httpRequestHelper.execPostRequest(params, url, new HashMap<String, String>());
        while (httpResponse.getStatusLine().getStatusCode() != 200){
            httpResponse = httpRequestHelper.execPostRequest(params, url, new HashMap<String, String>());
        }
        String returnMsg = EntityUtils.toString(httpResponse.getEntity());
        JSONObject json = JSON.parseObject(returnMsg);
        System.out.println("return Msg is " + returnMsg);
        String jsonData = json.getString("data");
        if (null == jsonData || "".equals(jsonData)) {
            System.out.println(orgFace + " -- " + desFace + " is failure");
            return;
        }
        JSONObject jsonObject = JSON.parseObject(jsonData);
        Float confidence = jsonObject.getFloat("avg");
//        int time_used = json.getInteger("time_used");
        List<String> orgDirList = Splitter.on("\\").trimResults().
                omitEmptyStrings().splitToList(orgFace);
        List<String> desDirList = Splitter.on("\\").trimResults().
                omitEmptyStrings().splitToList(desFace);
        if(confidence == null){
            face_detect_error.info("orgFace: {}, desFace: {}, face++ return meg: {}", orgFace, desFace, json);
            return;

        }
        total_log.info("orgFace: {}, desFace: {}, confidence: {}, time used: {}", orgFace, desFace, confidence, null);
        count ++;
        total_time_used += 0;
        String orgFileName = orgDirList.get(6);
        String desFileName = desDirList.get(6);
        if(orgDirList.get(5).equals(desDirList.get(5))){
            if(confidence > thresholds){
                success++;
                success_log.info("orgFace: {}, desFace: {}, confidence: {}", orgFileName, desFileName, confidence);
            }else{
                error++;
                error_log.info("orgFace: {}, desFace: {}, confidence: {}", orgFileName, desFileName, confidence);
            }
        }else{
            if(confidence > thresholds){
                error++;
                error_log.info("orgFace: {}, desFace: {}, confidence: {}", orgFileName, desFileName, confidence);
            }else{
                success++;
                success_log.info("orgFace: {}, desFace: {}, confidence: {}", orgFileName, desFileName, confidence);
            }
        }
        System.out.println(String.format("count: %d, error: %d, success: %d, total time used", count, error, success, null));

    }


    void traverseFolder2(String path, List<String> orgList, List<String> desList) {
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
