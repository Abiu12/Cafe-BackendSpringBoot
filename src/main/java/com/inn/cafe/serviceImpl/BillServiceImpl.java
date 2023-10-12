package com.inn.cafe.serviceImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.inn.cafe.POJO.Bill;
import com.inn.cafe.dao.BillDao;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.IOUtils;
import org.json.JSONArray;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.inn.cafe.JWT.JwtFilter;
import com.inn.cafe.constents.CafeConstants;

import com.inn.cafe.service.BillService;
import com.inn.cafe.utils.CafeUtils;
import com.itextpdf.awt.geom.Rectangle;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import ch.qos.logback.classic.Logger;

@Slf4j
@Service
public class BillServiceImpl implements BillService{
//    private Logger log= (Logger) LoggerFactory.getLogger(FacturaServiceImpl.class);

    @Autowired
    JwtFilter jwtFilter;

    @Autowired
    BillDao billDao;

    @Override
    public ResponseEntity<String> generateReport(Map<String, Object> requestMap) {
        log.info("Inside generarReporte");
        try {
            String fileName;
            if(validateRequestMao(requestMap)) {
                if(requestMap.containsKey("isGenerate") && !(Boolean) requestMap.get("isGenerate")) {
                    fileName=(String)requestMap.get("uuid");
                }else {
                    fileName = CafeUtils.getUUID();
                    requestMap.put("uuid", fileName);
                    insertBill(requestMap);
                }

                String data="Name: "+requestMap.get("name")+"\n"+"Contact Number: "+requestMap.get("contactNumber")+
                        "\n"+"email: "+requestMap.get("email")+"\n"+"Payment Method:"+requestMap.get("paymentMethod");

                Document document = new Document();
                PdfWriter.getInstance(document, new FileOutputStream(CafeConstants.STORE_LOCATION+"\\"+fileName+".pdf"));
                document.open();
                setRectangleInPdf(document);

                Paragraph chunk = new Paragraph("Cafe System Management",getFont("Header"));

                chunk.setAlignment(Element.ALIGN_CENTER);
                document.add(chunk);
                Paragraph paragrap= new Paragraph(data+"\n \n",getFont("data"));
                document.add(paragrap);

                PdfPTable table = new PdfPTable(5);

                table.setWidthPercentage(100);
                addTableHeader(table);

                ArrayList array = (ArrayList) requestMap.get("productDetails");


                // Convierte el ArrayList en un JSONArray
                JSONArray jsonArray = new JSONArray(array);

                // Ahora tienes tu lista en formato JSON
//                String jsonAsString = jsonArray.toString();
//                JSONArray jsonArray = CafeUtils.getJsonArrayFromString((String)requestMap.get("productDetails"));
                for(int i=0;i<jsonArray.length();i++) {
                    addRows(table,CafeUtils.getMapFromJson(jsonArray.getString(i)));
                }

                document.add(table);

                Paragraph footer = new Paragraph("Total : "+requestMap.get("totalAmount")+"\n"
                        +"Gracias por su compra, Vuelva pronto",getFont("Data"));
                document.add(footer);
                document.close();

                return new ResponseEntity<>("{\"uuid\":\""+fileName+"\"}",HttpStatus.OK);


            }
            return CafeUtils.getResponseEntity("Required data not found.", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void addRows(PdfPTable table, Map<String, Object> data) {
        log.info("Inside addrows");

        table.addCell((String) data.get("name"));
        table.addCell((String) data.get("category"));
        table.addCell((String) data.get("quantity"));
        table.addCell(Double.toString((Double)data.get("price")));
        table.addCell(Double.toString((Double)data.get("total")));


    }

    private void addTableHeader(PdfPTable table) {
        log.info("Inside addTableHeader");
        Stream.of("Name","Category","Quantity","Price","Sub Total").forEach(columnTitle->{
            PdfPCell header= new PdfPCell();
            header.setBackgroundColor(BaseColor.LIGHT_GRAY);
            header.setBorderWidth(2);
            header.setPhrase(new Phrase(columnTitle));
            header.setBackgroundColor(BaseColor.YELLOW);
            header.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(header);
        });

    }

    private Font getFont(String string) {
        log.info("Inside get Font");
        switch (string) {
            case "Header":
                Font headerFont= FontFactory.getFont(FontFactory.HELVETICA_BOLDOBLIQUE,18,BaseColor.BLACK);
                headerFont.setStyle(Font.BOLD);
                return headerFont;

            case "Data":
                Font dataFont = FontFactory.getFont(FontFactory.TIMES_ROMAN,11,BaseColor.BLACK);
                dataFont.setStyle(Font.BOLD);
                return dataFont;

            default:
                return new Font();
        }
    }

    private void setRectangleInPdf(Document document) throws DocumentException{
        log.info("Inside setRectangleInPdf");
        com.itextpdf.text.Rectangle rect = new com.itextpdf.text.Rectangle(557,825,18,15);
        rect.enableBorderSide(1);
        rect.enableBorderSide(2);
        rect.enableBorderSide(4);
        rect.enableBorderSide(8);
        //rect.setBackgroundColor(BaseColor.BLACK);
        rect.setBorderWidth(1);
        document.add(rect);


    }

    private void insertBill(Map<String, Object> requestMap) {
        try {
            Bill bill= new Bill();
            bill.setUuid((String)requestMap.get("uuid"));
            bill.setName((String) requestMap.get("name"));
            bill.setEmail((String) requestMap.get("email"));
            bill.setContactNumber((String) requestMap.get("contactNumber"));
            bill.setPaymentMethod((String) requestMap.get("paymentMethod"));
            bill.setTotal(Integer.parseInt((String)requestMap.get("totalAmount")));
            ArrayList array = (ArrayList) requestMap.get("productDetails");
            // Convierte el ArrayList en un JSONArray
            JSONArray jsonArray = new JSONArray(array);
            bill.setProductDetails(String.valueOf(jsonArray));
            bill.setCreatedBy(jwtFilter.getCurrentUser());
            billDao.save(bill);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }

    }

    private boolean validateRequestMao(Map<String, Object> requestMap) {
        return requestMap.containsKey("name") && requestMap.containsKey("contactNumber")&&
                requestMap.containsKey("email") && requestMap.containsKey("paymentMethod") &&
                requestMap.containsKey("productDetails") &&
                requestMap.containsKey("total");
    }

    @Override
    public ResponseEntity<List<Bill>> getBills() {
        // TODO Auto-generated method stub
        List<Bill> list = new ArrayList<>();

        if(jwtFilter.isAdmin()) {
            list= billDao.getAllBills();
        }else {
            list = billDao.getBillByUserName(jwtFilter.getCurrentUser());
        }
        return new ResponseEntity<>(list,HttpStatus.OK);
    }

    @Override
    public ResponseEntity<byte[]> getPdf(Map<String, Object> requestMap) {
        log.info("Inside getPdf : requestMap {}",requestMap);
        try {
            byte[] byteArray= new byte[0];
            if(!requestMap.containsKey("uuid") && validateRequestMao(requestMap))
                return new ResponseEntity<>(byteArray,HttpStatus.BAD_REQUEST);
            String filePath= CafeConstants.STORE_LOCATION+"\\"+(String) requestMap.get("uuid")+".pdf";

            if(CafeUtils.isFileExist(filePath)) {
                byteArray= getByteArray(filePath);
                return new ResponseEntity<>(byteArray,HttpStatus.OK);
            }else {
                requestMap.put("isGenerate", false);
                generateReport(requestMap);
                byteArray= getByteArray(filePath);
                return new ResponseEntity<> (byteArray,HttpStatus.OK);
            }

        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return null;
    }

    private byte[] getByteArray(String filePath) throws Exception{
        File initialFile = new File(filePath);
        InputStream stream = new FileInputStream(initialFile);
        byte[] byteArray= IOUtils.toByteArray(stream);
        stream.close();
        return byteArray;
    }

    @Override
    public ResponseEntity<String> deleteBill(Integer id) {
        try {
            Optional<Bill> optional= billDao.findById(id);
            if(!optional.isEmpty()) {
                billDao.deleteById(id);
                return CafeUtils.getResponseEntity("Factura eliminada corectamente", HttpStatus.OK);
            }
            return CafeUtils.getResponseEntity("Factura con el id no existe", HttpStatus.OK);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
