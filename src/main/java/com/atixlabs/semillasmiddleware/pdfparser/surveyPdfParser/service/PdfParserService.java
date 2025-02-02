package com.atixlabs.semillasmiddleware.pdfparser.surveyPdfParser.service;

import com.atixlabs.semillasmiddleware.app.model.excel.FormPDF;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.Category;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.EntrepreneurshipCategory;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.PersonCategory;
import com.atixlabs.semillasmiddleware.excelparser.app.constants.Categories;
import com.atixlabs.semillasmiddleware.excelparser.app.constants.PDFQuestions;
import com.atixlabs.semillasmiddleware.excelparser.app.dto.SurveyForm;
import com.atixlabs.semillasmiddleware.pdfparser.util.PDFUtil;
import com.atixlabs.semillasmiddleware.util.EmailTemplatesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import java.util.*;


@Service
@Slf4j
public class PdfParserService {

    private static String fullName = "";

    public List<String> generatePDFFromKobo(List<FormPDF> data){
        List<String> pdfsGenerated = new ArrayList<>();
        List<String> structure = PDFUtil.generateData(FormPDF.class.getDeclaredFields());

        // Limpia claves innecesarias
        structure.remove("index");
        structure.remove("fecha");

        log.info("Creating pdf from kobo form");
        for (FormPDF form: data) {
            String template = "";
            Map<String, Object> info = PDFUtil.generateKeys(form);
            String html = generateHtmlFromStack(structure, info);
            template += EmailTemplatesUtil.getTemplate(PDFUtil.TEMPLATE_NAME)
                    .replace(PDFUtil.TABLE_CONTENT_PARAM, html)
                    .replace(PDFUtil.SEMILLAS_IMAGE_BASE64_PARAM, "data:image/svg+xml;base64,"
                            + Base64.getEncoder().encodeToString(EmailTemplatesUtil.getImage(PDFUtil.IMG_LOGO_NAME).getBytes()))
                    .replace(PDFUtil.CREATE_DATE_PARAM, info.get("fecha").toString());
            pdfsGenerated.add(PDFUtil.createTemporaryPDF(PDFUtil.PDF_SUFFIX.concat(fullName), template));
        }
        return pdfsGenerated;
    }

    private String generateHtmlFromStack(List<String> structure, Map<String, Object> data){
        String htmlStack = "";
        fullName = data.get("nombreBeneficiario").toString().concat("_").concat(data.get("apellidoBeneficiario").toString());

        for (String slot: structure) {
            if (slot.startsWith("Cat_")){
                log.info("Create html from Category: "+ slot);
                if (data.get(StringUtils.unCapitalize(slot))!=null) htmlStack += PDFUtil.headerTemplate.replace(PDFUtil.CATEGORY_NAME_PARAM, data.get(StringUtils.unCapitalize(slot)).toString());
            }else if (slot.startsWith("SubCat_")){
                log.info("Create html from Sub Category: "+ slot);
                if (data.get(StringUtils.unCapitalize(slot))!=null) htmlStack += PDFUtil.subCategoryTemplate.replace(PDFUtil.SUBCATEGORY_PARAM, data.get(StringUtils.unCapitalize(slot)).toString());
            }else{
                if (data.get(StringUtils.unCapitalize(slot)) != null){
                    htmlStack += generateHtmlContent(slot, parseAnswer(data.get(StringUtils.unCapitalize(slot)).toString()));
                }
            }
        }

        return htmlStack;
    }

    private String generateHtmlContent(String slot, String answer){
        switch (slot){
            case "Hijos" :
            case "FamilyMembers":
            case "FamilyMemberIncome":
            case "CreditosEmprendimiento":
            case "CreditosFamiliares":
                return answer;

            default:
                return PDFUtil.rowTemplate.replace(PDFUtil.QUESTION_PARAM, PDFQuestions.valueOf(slot).getQuestion()).replace(PDFUtil.ANSWER_PARAM, answer);
        }
    }

    public String parseAnswer(String answer){
        switch (answer){
            case "true":
                return "Si";

            case "false":
                return "No";

            default:
                return answer;
        }
    }

    public String generatePdfFromSurvey(SurveyForm surveyForm) {
        log.info("Creating pdf from survey form");
        Stack<Category> categoriesStack = new Stack<>();
        // top down order
        fillStack(categoriesStack, Categories.getCodeList(), surveyForm);

        PersonCategory beneficiaryCategory = (PersonCategory) surveyForm
                .getCategoryByUniqueName(Categories.BENEFICIARY_CATEGORY_NAME.getCode(), null);

        String html = generateHtmlFromCategories(categoriesStack);
        String createDate = surveyForm.getSurveyDate().toString();
        String template = EmailTemplatesUtil.getTemplate(PDFUtil.TEMPLATE_NAME)
                .replace(PDFUtil.TABLE_CONTENT_PARAM, html)
                .replace(PDFUtil.SEMILLAS_IMAGE_BASE64_PARAM, "data:image/svg+xml;base64,"
                        + Base64.getEncoder().encodeToString(EmailTemplatesUtil.getImage(PDFUtil.IMG_LOGO_NAME).getBytes()))
                .replace(PDFUtil.CREATE_DATE_PARAM,createDate);

        return PDFUtil.createTemporaryPDF(PDFUtil.PDF_SUFFIX + beneficiaryCategory.getFullName(), template);
    }

    private String generateHtmlFromCategories(Stack<Category> categoriesStack){
        String htmlStack = "";
        while (!categoriesStack.empty()){
            Category category = categoriesStack.pop();
            if (category == null) continue;
            log.info("Create html from category: "+category.getCategoryName());
            String header = PDFUtil.headerTemplate.replace(PDFUtil.CATEGORY_NAME_PARAM, category.getCategoryUniqueName());
            String rows = category.getHtmlFromTemplate(PDFUtil.rowTemplate, PDFUtil.subCategoryTemplate, PDFUtil.SUBCATEGORY_PARAM, PDFUtil.QUESTION_PARAM, PDFUtil.ANSWER_PARAM);
            htmlStack += header+rows;
        }
        return htmlStack;
    }


    private void fillStack(Stack<Category> categoriesStack, List<Categories> categories, SurveyForm surveyForm){
        Collections.reverse(categories);
        categories.forEach( cat -> {
            for(int i = cat.getAmount(); i >= 1; i--) {
                String name = cat.getAmount() > 1 ? String.format("%s %d", cat.getCode(), i) : cat.getCode();
                Optional.ofNullable(surveyForm.getCategoryByUniqueName(name, null))
                        .ifPresent(categoriesStack::push);
            }
        });

    }

}
