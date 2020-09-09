package com.atixlabs.semillasmiddleware.pdfparser.surveyPdfParser.service;

import com.atixlabs.semillasmiddleware.excelparser.app.categories.Category;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.EntrepreneurshipCategory;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.PersonCategory;
import com.atixlabs.semillasmiddleware.excelparser.app.constants.Categories;
import com.atixlabs.semillasmiddleware.excelparser.app.dto.SurveyForm;
import com.atixlabs.semillasmiddleware.pdfparser.util.PDFUtil;
import com.atixlabs.semillasmiddleware.util.EmailTemplatesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Stack;


@Service
@Slf4j
public class PdfParserService {

    private static final String TEMPLATE_NAME = "excell_survey_data.html";
    private static final String PDF_SUFFIX = "Encuesta_";
    private static final String CATEGORY_NAME_PARAM = "{{categoryName}}";
    private static final String TABLE_CONTENT_PARAM = "{{tableContent}}";
    private static final String QUESTION_PARAM = "{{question}}";
    private static final String ANSWER_PARAM = "{{answer}}";

    private static final String style = "style=\"border: 1px solid #FFFFFF ; border-collapse: collapse; padding: 5px\"\"";
    private String rowTemplate = "        <tr "+style+">\n" +
                                 "            <td>{{question}}</td>\n" +
                                 "            <td>{{answer}}</td>\n" +
                                 "        </tr>";

    private String headerTemplate = "        <tr "+style+">\n" +
                                    "            <th colspan=\"2\">\n" +
                                    "                <br />"+CATEGORY_NAME_PARAM+"\n" +
                                    "            </th>\n" +
                                    "        </tr>\n" +
                                    "        <th>PREGUNTA</th>\n" +
                                    "        <th>RESPUESTA</th>\n";

    public String generatePdfFromSurvey(SurveyForm surveyForm){
        log.info("Creating pdf from survey form");
        Stack<Category> categoriesStack = new Stack<>();
        //top down order
        fillStack(categoriesStack, Categories.getCodeList(), surveyForm);

        PersonCategory beneficiaryCategory = (PersonCategory) surveyForm.getCategoryByUniqueName(Categories.BENEFICIARY_CATEGORY_NAME.getCode(), null);

        String html = generateHtmlFromCategories(categoriesStack);
        String template = EmailTemplatesUtil.getTemplate(TEMPLATE_NAME).replace(TABLE_CONTENT_PARAM, html);

        return PDFUtil.createTemporaryPdf(PDF_SUFFIX+beneficiaryCategory.getFullName(), template);
    }

    private String generateHtmlFromCategories(Stack<Category> categoriesStack){
        String htmlStack = "";
        while (!categoriesStack.empty()){
            Category category = categoriesStack.pop();
            if (category == null) continue;
            log.info("Create html from category: "+category.getCategoryName());
            String header = headerTemplate.replace(CATEGORY_NAME_PARAM, category.getCategoryUniqueName());
            String rows = category.getHtmlFromTemplate(rowTemplate, QUESTION_PARAM, ANSWER_PARAM);
            htmlStack += header+rows;
        }
        return htmlStack;
    }

    private void fillStack(Stack<Category> categoriesStack, List<Categories> categories, SurveyForm surveyForm){
        categories.forEach( cat -> {
            Optional.ofNullable(surveyForm.getCategoryByUniqueName(cat.getCode(), null))
                .ifPresent(categoriesStack::push);
        });

    }
}
