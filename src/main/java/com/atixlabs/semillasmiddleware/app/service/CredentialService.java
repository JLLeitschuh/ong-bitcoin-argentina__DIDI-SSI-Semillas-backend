package com.atixlabs.semillasmiddleware.app.service;

import com.atixlabs.semillasmiddleware.app.bondarea.model.Loan;
import com.atixlabs.semillasmiddleware.app.bondarea.repository.LoanRepository;
import com.atixlabs.semillasmiddleware.app.exceptions.NoExpiredConfigurationExists;
import com.atixlabs.semillasmiddleware.app.exceptions.PersonDoesNotExists;
import com.atixlabs.semillasmiddleware.app.model.DIDHistoric.DIDHisotoric;
import com.atixlabs.semillasmiddleware.app.model.beneficiary.Person;
import com.atixlabs.semillasmiddleware.app.model.configuration.ParameterConfiguration;
import com.atixlabs.semillasmiddleware.app.model.credential.*;
import com.atixlabs.semillasmiddleware.app.model.credential.constants.CredentialCategoriesCodes;
import com.atixlabs.semillasmiddleware.app.model.credential.constants.CredentialStatesCodes;
import com.atixlabs.semillasmiddleware.app.model.credential.constants.CredentialTypesCodes;
import com.atixlabs.semillasmiddleware.app.model.credentialState.CredentialState;
import com.atixlabs.semillasmiddleware.app.repository.*;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.Category;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.DwellingCategory;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.EntrepreneurshipCategory;
import com.atixlabs.semillasmiddleware.app.model.credential.CredentialBenefits;
import com.atixlabs.semillasmiddleware.app.model.credential.CredentialCredit;
import com.atixlabs.semillasmiddleware.app.repository.CredentialCreditRepository;
import com.atixlabs.semillasmiddleware.app.repository.PersonRepository;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.AnswerCategoryFactory;
import com.atixlabs.semillasmiddleware.excelparser.app.categories.PersonCategory;
import com.atixlabs.semillasmiddleware.excelparser.app.constants.Categories;
import com.atixlabs.semillasmiddleware.excelparser.app.dto.SurveyForm;
import com.atixlabs.semillasmiddleware.app.model.credential.constants.*;
import com.atixlabs.semillasmiddleware.excelparser.dto.ProcessExcelFileResult;
import com.atixlabs.semillasmiddleware.app.model.credential.Credential;
import com.atixlabs.semillasmiddleware.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.Opt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.atixlabs.semillasmiddleware.app.model.credential.constants.CredentialTypesCodes.*;

@Slf4j
@Service
public class CredentialService {

    private CredentialRepository credentialRepository;
    private CredentialCreditRepository credentialCreditRepository;
    private CredentialIdentityRepository credentialIdentityRepository;
    private CredentialEntrepreneurshipRepository credentialEntrepreneurshipRepository;
    private CredentialDwellingRepository credentialDwellingRepository;
    private PersonRepository personRepository;
    private LoanRepository loanRepository;
    private CredentialBenefitsRepository credentialBenefitsRepository;
    private DIDHistoricRepository didHistoricRepository;
    private CredentialStateRepository credentialStateRepository;
    private ParameterConfigurationRepository parameterConfigurationRepository;
    private AnswerCategoryFactory answerCategoryFactory;


    @Autowired
    public CredentialService(
                CredentialCreditRepository credentialCreditRepository,
                CredentialRepository credentialRepository,
                PersonRepository personRepository,
                LoanRepository loanRepository,
                CredentialBenefitsRepository credentialBenefitsRepository,
                DIDHistoricRepository didHistoricRepository,
                CredentialStateRepository credentialStateRepository,
                AnswerCategoryFactory answerCategoryFactory,
                CredentialIdentityRepository credentialIdentityRepository,
                CredentialEntrepreneurshipRepository credentialEntrepreneurshipRepository,
                CredentialDwellingRepository credentialDwellingRepository,
                ParameterConfigurationRepository parameterConfigurationRepository
        ) {
            this.credentialCreditRepository = credentialCreditRepository;
            this.credentialRepository = credentialRepository;
            this.personRepository = personRepository;
            this.loanRepository = loanRepository;
            this.credentialBenefitsRepository = credentialBenefitsRepository;
            this.didHistoricRepository = didHistoricRepository;
            this.credentialStateRepository = credentialStateRepository;
            this.parameterConfigurationRepository = parameterConfigurationRepository;
            this.answerCategoryFactory = answerCategoryFactory;
            this.credentialIdentityRepository = credentialIdentityRepository;
            this.credentialEntrepreneurshipRepository = credentialEntrepreneurshipRepository;
            this.credentialDwellingRepository = credentialDwellingRepository;
        }


    public void buildAllCredentialsFromForm(SurveyForm surveyForm, ProcessExcelFileResult processExcelFileResult) {
        log.info("buildAllCredentialsFromForm: " + this.toString());
        if (validateAllCredentialsFromForm(surveyForm, processExcelFileResult))
            saveAllCredentialsFromForm(surveyForm);
    }


    /**
     * The following are non-public methods, isolating functionality.
     * to make public methods easier to read.
     *
     * @param surveyForm
     */
    private boolean validateAllCredentialsFromForm(SurveyForm surveyForm, ProcessExcelFileResult processExcelFileResult) {
        log.info("  validateIdentityCredentialFromForm");

        //1-get all people data from form, creditHolder will be a beneficiary as well.
        ArrayList<Category> categoryArrayList = surveyForm.getAllCompletedCategories();

        //2-get creditHolder Data
        PersonCategory creditHolderPersonCategory = (PersonCategory) surveyForm.getCategoryByUniqueName(Categories.BENEFICIARY_CATEGORY_NAME.getCode(), null);
        Person creditHolder = Person.getPersonFromPersonCategory(creditHolderPersonCategory);

        //2-verify each person is new, or his data has not changed.
        boolean allCredentialsNewOrInactive = true;
        for (Category category : categoryArrayList) {
            switch (category.getCategoryName()) {
                case BENEFICIARY_CATEGORY_NAME:
                case SPOUSE_CATEGORY_NAME:
                case CHILD_CATEGORY_NAME:
                case KINSMAN_CATEGORY_NAME:
                    PersonCategory beneficiaryPersonCategory = (PersonCategory) category;
                    Person beneficiary = Person.getPersonFromPersonCategory(beneficiaryPersonCategory);
                    if (isCredentialAlreadyExistent(beneficiary.getDocumentNumber(), CredentialCategoriesCodes.IDENTITY.getCode(), processExcelFileResult))
                        allCredentialsNewOrInactive = false;
                    break;
                case ENTREPRENEURSHIP_CATEGORY_NAME:
                    if (isCredentialAlreadyExistent(creditHolder.getDocumentNumber(), CredentialCategoriesCodes.ENTREPRENEURSHIP.getCode(), processExcelFileResult))
                        allCredentialsNewOrInactive = false;
                    break;
                case DWELLING_CATEGORY_NAME:
                    if (isCredentialAlreadyExistent(creditHolder.getDocumentNumber(), CredentialCategoriesCodes.DWELLING.getCode(), processExcelFileResult))
                        allCredentialsNewOrInactive = false;
                    break;
            }
        }
        return allCredentialsNewOrInactive;
    }

    private boolean isCredentialAlreadyExistent(Long beneficiaryDni, String credentialCategoryCode, ProcessExcelFileResult processExcelFileResult) {

        ArrayList<String> statesCodesToFind = new ArrayList<>();
        statesCodesToFind.add(CredentialStatesCodes.PENDING_DIDI.getCode());
        statesCodesToFind.add(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode());

        List<CredentialState> credentialStateActivePending = credentialStateRepository.findByStateNameIn(statesCodesToFind);

        Optional<Credential> credentialOptional = credentialRepository.findByBeneficiaryDniAndAndCredentialCategoryAndCredentialStateIn(
                beneficiaryDni,
                credentialCategoryCode,
                credentialStateActivePending
        );
        if (credentialOptional.isEmpty())
            return false;
        else
            processExcelFileResult.addRowError(
                    "Warning CREDENCIAL DUPLICADA",
                    "Ya existe una credencial de tipo " + credentialCategoryCode +
                            " en estado " + credentialOptional.get().getCredentialState().getStateName()+
                            " para el DNI " + beneficiaryDni + " si desea continuar debe revocarlas manualmente"
            );
        return true;
    }

    private void saveAllCredentialsFromForm(SurveyForm surveyForm) {
        //1-get creditHolder Data
        PersonCategory creditHolderPersonCategory = (PersonCategory) surveyForm.getCategoryByUniqueName(Categories.BENEFICIARY_CATEGORY_NAME.getCode(), null);
        Person creditHolder = Person.getPersonFromPersonCategory(creditHolderPersonCategory);

        //1-get all data from form
        ArrayList<Category> categoryArrayList = surveyForm.getAllCompletedCategories();

        //4-Now working with each beneficiary
        for (Category category : categoryArrayList) {
            saveCredential(category, creditHolder);
        }
    }


    private void saveCredential(Category category, Person creditHolder) {
        log.info("  saveCredential: " + category.getCategoryName());
        switch (category.getCategoryName()) {
            case BENEFICIARY_CATEGORY_NAME:
            case CHILD_CATEGORY_NAME:
            case SPOUSE_CATEGORY_NAME:
            case KINSMAN_CATEGORY_NAME:
                credentialIdentityRepository.save(buildIdentityCredential(category, creditHolder));
                break;
            case ENTREPRENEURSHIP_CATEGORY_NAME:
                credentialEntrepreneurshipRepository.save(buildEntrepreneurshipCredential(category, creditHolder));
                break;
            case DWELLING_CATEGORY_NAME:
                credentialDwellingRepository.save(buildDwellingCredential(category, creditHolder));
                break;
        }
    }

    public List<Credential> findCredentials(String credentialType, String name, String dniBeneficiary, String
            idDidiCredential, String dateOfExpiry, String dateOfIssue, List<String> credentialState) {
        List<Credential> credentials;
        credentials = credentialRepository.findCredentialsWithFilter(credentialType, name, dniBeneficiary, idDidiCredential, dateOfExpiry, dateOfIssue, credentialState);

        return credentials;
    }

    private Person savePersonIfNew(Person person) {
        Optional<Person> personOptional = personRepository.findByDocumentNumber(person.getDocumentNumber());
        if (personOptional.isEmpty())
            return personRepository.save(person);
        if (!(person.equalsIgnoreId(person, personOptional.get()))) {
            person.setId(personOptional.get().getId());
            return personRepository.save(person);
        }
        return personOptional.get();
    }

    private void buildCredential(Person creditHolder, Credential credential) {
        creditHolder = savePersonIfNew(creditHolder);

        credential.setDateOfIssue(DateUtil.getLocalDateTimeNow());
        credential.setCreditHolder(creditHolder);
        credential.setCreditHolderDni(creditHolder.getDocumentNumber());
        credential.setCreditHolderName(creditHolder.getFirstName() + " " + creditHolder.getLastName());

        //the beneficiary is the same as the credit holder for all credentials but identity
        //buildIdentityCredential overwrites this value with the different members.
        credential.setBeneficiary(creditHolder);
        credential.setBeneficiaryDni(creditHolder.getDocumentNumber());
        credential.setBeneficiaryName(creditHolder.getFirstName() + " " + creditHolder.getLastName());

        //credential.setCredentialStatus(CredentialStatusCodes.CREDENTIAL_PENDING_BONDAREA.getCode());
        Optional<CredentialState> credentialStateOptional = credentialStateRepository.findByStateName(CredentialStatesCodes.PENDING_DIDI.getCode());
        credentialStateOptional.ifPresent(credential::setCredentialState);
    }

    private CredentialIdentity buildIdentityCredential(Category category, Person creditHolder) {
        PersonCategory beneficiaryPersonCategory = (PersonCategory) category;
        Person beneficiary = Person.getPersonFromPersonCategory(beneficiaryPersonCategory);
        beneficiary = savePersonIfNew(beneficiary);

        CredentialIdentity credentialIdentity = new CredentialIdentity();
        buildCredential(creditHolder, credentialIdentity);

        credentialIdentity.setBeneficiary(beneficiary);
        credentialIdentity.setBeneficiaryDni(beneficiary.getDocumentNumber());
        credentialIdentity.setBeneficiaryName(beneficiary.getFirstName() + " " + beneficiary.getLastName());

        credentialIdentity.setCredentialCategory(CredentialCategoriesCodes.IDENTITY.getCode());

        switch (beneficiaryPersonCategory.getPersonType()) {
            case BENEFICIARY:
                credentialIdentity.setCredentialDescription(CredentialTypesCodes.CREDENTIAL_IDENTITY.getCode());
                break;
            case SPOUSE:
            case CHILD:
            case OTHER_KINSMAN:
                credentialIdentity.setCredentialDescription(CredentialTypesCodes.CREDENTIAL_IDENTITY_FAMILY.getCode());
                break;
        }

        return credentialIdentity;
    }

    private CredentialEntrepreneurship buildEntrepreneurshipCredential(Category category, Person creditHolder) {
        EntrepreneurshipCategory entrepreneurshipCategory = (EntrepreneurshipCategory) category;

        CredentialEntrepreneurship credentialEntrepreneurship = new CredentialEntrepreneurship();
        buildCredential(creditHolder, credentialEntrepreneurship);
        credentialEntrepreneurship.setEntrepreneurshipType(entrepreneurshipCategory.getType());
        credentialEntrepreneurship.setStartActivity(entrepreneurshipCategory.getActivityStartDate());
        credentialEntrepreneurship.setMainActivity(entrepreneurshipCategory.getMainActivity());
        credentialEntrepreneurship.setEntrepreneurshipName(entrepreneurshipCategory.getName());
        credentialEntrepreneurship.setEntrepreneurshipAddress(entrepreneurshipCategory.getAddress());
        credentialEntrepreneurship.setEndActivity(entrepreneurshipCategory.getActivityEndingDate());

        credentialEntrepreneurship.setCredentialCategory(CredentialCategoriesCodes.ENTREPRENEURSHIP.getCode());
        credentialEntrepreneurship.setCredentialDescription(CredentialCategoriesCodes.ENTREPRENEURSHIP.getCode());

        return credentialEntrepreneurship;
    }

    private CredentialDwelling buildDwellingCredential(Category category, Person creditHolder) {
        DwellingCategory entrepreneurshipCategory = (DwellingCategory) category;

        CredentialDwelling credentialDwelling = new CredentialDwelling();
        buildCredential(creditHolder, credentialDwelling);

        credentialDwelling.setDwellingType(entrepreneurshipCategory.getDwellingType());
        credentialDwelling.setDwellingAddress(entrepreneurshipCategory.getDistrict());
        credentialDwelling.setPossessionType(entrepreneurshipCategory.getHoldingType());

        credentialDwelling.setCredentialCategory(CredentialCategoriesCodes.DWELLING.getCode());
        credentialDwelling.setCredentialDescription(CredentialCategoriesCodes.DWELLING.getCode());

        return credentialDwelling;
    }

    /**
     * Create a new credential credit if the id bondarea of the credit does not exist.
     * Then it creates the benefits credential to the holder
     * @param loan
     * @throws PersonDoesNotExists
     */
    public void createNewCreditCredentials(Loan loan) throws PersonDoesNotExists {
        //beneficiarieSSSS -> the credit group will be created by separate (not together)
        log.info("Creating Credential Credit ");
        Optional<CredentialCredit> opCreditExistence = credentialCreditRepository.findByIdBondareaCredit(loan.getIdBondareaLoan());
        if (opCreditExistence.isEmpty()) {
            Optional<Person> opBeneficiary = personRepository.findByDocumentNumber(loan.getDniPerson());
            if (opBeneficiary.isPresent()) {
                CredentialCredit credit = this.buildCreditCredential(loan, opBeneficiary.get());
                loan.setHasCredential(true);

                credit = credentialCreditRepository.save(credit);
                //get the new id and save it on id historic
                credit.setIdHistorical(credit.getId());
                credentialCreditRepository.save(credit);
                log.info("Credential Credit created for dni: " + opBeneficiary.get().getDocumentNumber());

                loanRepository.save(loan);

                //after create credit, will create benefit holder credential
                this.createNewBenefitsCredential(opBeneficiary.get(), PersonTypesCodes.HOLDER);
            } else {
                log.error("Person with dni "+ loan.getDniPerson() + " has not been created. The loan exists but the survey with this person has not been loaded");
                throw new PersonDoesNotExists("Person with dni " + loan.getDniPerson() + " has not been created. The loan exists but the survey with this person has not been loaded");
                //this error is important, have to be shown in front
            }
        } else {
            loan.setHasCredential(true);
            loanRepository.save(loan);
            log.error("The credit with idBondarea " + loan.getIdBondareaLoan() + " has an existent credential");
        }
    }


    private CredentialCredit buildCreditCredential(Loan loan, Person beneficiary){

        CredentialCredit credentialCredit = new CredentialCredit();
        credentialCredit.setIdBondareaCredit(loan.getIdBondareaLoan());
        // TODO we need the type from bondarea - credentialCredit.setCreditType();
        credentialCredit.setIdGroup(loan.getIdGroup());
        credentialCredit.setCurrentCycle(loan.getCycleDescription()); // si cambia, se tomara como cambio de ciclo
        //TODO data for checking - credentialCredit.totalCycles;

        credentialCredit.setAmountExpiredCycles(0);
        credentialCredit.setCreditState(loan.getStatusDescription());
        credentialCredit.setExpiredAmount(loan.getExpiredAmount());
        credentialCredit.setCreationDate(loan.getCreationDate());

        //Added Modification CreditHolderDni and CreditHolderId
        credentialCredit.setBeneficiary(beneficiary);
        credentialCredit.setBeneficiaryDni(beneficiary.getDocumentNumber());
        credentialCredit.setBeneficiaryName(beneficiary.getFirstName()+" "+beneficiary.getLastName());

        credentialCredit.setCreditHolderDni(beneficiary.getDocumentNumber());
        credentialCredit.setCreditHolder(beneficiary);
        credentialCredit.setCreditHolderName(beneficiary.getFirstName()+" "+beneficiary.getLastName());
        //End creditHolder changes

        //Credential Parent fields
        credentialCredit.setDateOfIssue(DateUtil.getLocalDateTimeNow());
        credentialCredit.setBeneficiary(beneficiary);


        //TODO this should be took from DB - credentialCredit.setIdDidiIssuer();
        Optional<DIDHisotoric> opActiveDid = didHistoricRepository.findByIdPersonAndIsActive(beneficiary.getId(), true);
        if (opActiveDid.isPresent()) {
            credentialCredit.setIdDidiReceptor(opActiveDid.get().getIdDidiReceptor());
            credentialCredit.setIdDidiCredential(opActiveDid.get().getIdDidiReceptor());
            Optional<CredentialState> opStateActive = credentialStateRepository.findByStateName(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode());
            if (opStateActive.isPresent()) {
                credentialCredit.setCredentialState(opStateActive.get());
            }

        } else {
            //Person do not have a DID yet -> set as pending didi
            Optional<CredentialState> opStateActive = credentialStateRepository.findByStateName(CredentialStatesCodes.PENDING_DIDI.getCode());
            if (opStateActive.isPresent()) {
                credentialCredit.setCredentialState(opStateActive.get());
            }
        }

        //This depends of the type of loan from bondarea
        credentialCredit.setCredentialDescription(CredentialTypesCodes.CREDENTIAL_CREDIT.getCode());
        credentialCredit.setCredentialCategory(CredentialTypesCodes.CREDENTIAL_CREDIT.getCode());// TODO this column will be no longer useful

        return credentialCredit;
    }


    public void createNewBenefitsCredential(Person beneficiary, PersonTypesCodes personType) {
        log.info("Creating Credential Benefits");
        List<CredentialBenefits> opBenefits = credentialBenefitsRepository.findByBeneficiaryDni(beneficiary.getDocumentNumber());
        //filter the active or pending benefits
        List<CredentialBenefits> benefitsActiveOrPending = opBenefits.stream().filter(credentialBenefits -> (credentialBenefits.getCredentialState().getStateName().equals(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode()) || (credentialBenefits.getCredentialState().getStateName().equals(CredentialStatesCodes.PENDING_DIDI.getCode())))).collect(Collectors.toList());
        //filter the exact benefits type
        List<CredentialBenefits> benefitsTypeActiveOrPending = benefitsActiveOrPending.stream().filter(credentialBenefit -> credentialBenefit.getBeneficiaryType().equals(personType.getCode())).collect(Collectors.toList());
        //create benefit if person does not have one or | do not have the type wanted to create. Or is not Active nor pending
        if (benefitsTypeActiveOrPending.size() == 0) {
            CredentialBenefits benefits = this.buildBenefitsCredential(beneficiary, personType);

            //get the new id and save it on id historic
            benefits = credentialBenefitsRepository.save(benefits);
            benefits.setIdHistorical(benefits.getId());
            credentialBenefitsRepository.save(benefits);
            log.info("Credential Credit created for dni: " + beneficiary.getDocumentNumber());

        } else {
            log.info("Person with dni " + beneficiary.getDocumentNumber() + " already has a credential benefits");
        }
    }



    public CredentialBenefits buildBenefitsCredential(Person beneficiary, PersonTypesCodes personType){
            CredentialBenefits benefits = new CredentialBenefits();

            //Person is holder or family
            if (personType.equals(PersonTypesCodes.HOLDER)) {
                benefits.setBeneficiaryType(PersonTypesCodes.HOLDER.getCode());
                benefits.setCredentialCategory(CredentialTypesCodes.CREDENTIAL_BENEFITS.getCode());
                benefits.setCredentialDescription(CredentialTypesCodes.CREDENTIAL_BENEFITS.getCode());
            } else {
                benefits.setBeneficiaryType(PersonTypesCodes.FAMILY.getCode());
                benefits.setCredentialCategory(CredentialTypesCodes.CREDENTIAL_BENEFITS_FAMILY.getCode());
                benefits.setCredentialDescription(CredentialTypesCodes.CREDENTIAL_BENEFITS_FAMILY.getCode());
            }

            benefits.setDateOfIssue(DateUtil.getLocalDateTimeNow());


            //Added Modification CreditHolderDni and CreditHolderId
            benefits.setBeneficiary(beneficiary);
            benefits.setBeneficiaryDni(beneficiary.getDocumentNumber());
            benefits.setBeneficiaryName(beneficiary.getFirstName() + " " + beneficiary.getLastName());


            benefits.setCreditHolderDni(beneficiary.getDocumentNumber());
            benefits.setCreditHolder(beneficiary);
            benefits.setCreditHolderName(beneficiary.getFirstName() + " " + beneficiary.getLastName());
            //End creditHolder changes

            //TODO credentialCredit.setIdHistorical();
            //TODO this should be took from DB - credentialCredit.setIdDidiIssuer();

            Optional<DIDHisotoric> opActiveDid = didHistoricRepository.findByIdPersonAndIsActive(beneficiary.getId(), true);
            if (opActiveDid.isPresent()) {
                benefits.setIdDidiReceptor(opActiveDid.get().getIdDidiReceptor());
                benefits.setIdDidiCredential(opActiveDid.get().getIdDidiReceptor());
                Optional<CredentialState> opStateActive = credentialStateRepository.findByStateName(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode());
                if (opStateActive.isPresent()) {
                    benefits.setCredentialState(opStateActive.get());
                }
            } else {
                //Person do not have a DID yet -> set as pending didi
                Optional<CredentialState> opStateActive = credentialStateRepository.findByStateName(CredentialStatesCodes.PENDING_DIDI.getCode());
                if (opStateActive.isPresent())
                    benefits.setCredentialState(opStateActive.get());
            }

            return benefits;
        }

    /**
     * Validate if the credential needs to be updated.
     * @param loan
     * @return the credit or null
     */
    public CredentialCredit validateCredentialCreditToUpdate(Loan loan){
        Optional<CredentialState> opStateActive = credentialStateRepository.findByStateName(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode());
        Optional<CredentialState> opStatePending = credentialStateRepository.findByStateName(CredentialStatesCodes.PENDING_DIDI.getCode());
        if(opStateActive.isPresent() && opStatePending.isPresent()) {
            Optional<CredentialCredit> opCredit = credentialCreditRepository.findByIdBondareaCreditAndCredentialStateIn(loan.getIdBondareaLoan(), List.of(opStateActive.get(), opStatePending.get()));
            if (opCredit.isPresent()) {
                CredentialCredit credit = opCredit.get();
                if (!(Float.compare(loan.getExpiredAmount(), credit.getExpiredAmount()) == 0) || !loan.getCycleDescription().equals(credit.getCurrentCycle()) || loan.getStatusDescription() != credit.getCreditState()/*||  loan.getTotalCuotas...*/) {
                    // the loan has changed, return credit to be update
                    return credit;
                } else {
                    return null;
                }
            } else {
                // the credit had been set that has a credential credit, but no credential credit exist with the bondarea id
                // the next time loans are going to be check, a new credential credit would be create
                loan.setHasCredential(false);
                loanRepository.save(loan);
                return null;
            }
        }
        return null;
    }


    /**
     * 2nd Step in the process, after create the new credits. This process will check the previous credential credit and his loan, to update and | or revoke.
     * If there has been a change the credentials is revoked and generated a new one.
     *
     * @param loan
     * @param credit
     */
    public void updateCredentialCredit(Loan loan, CredentialCredit credit) throws NoExpiredConfigurationExists, PersonDoesNotExists{
        // revoke credit -> save id historic
        Long idHistoricCredit = credit.getIdHistorical();
        revokeOneCredential(credit);

        Optional<Person> opBeneficiary = personRepository.findByDocumentNumber(loan.getDniPerson());
        if (opBeneficiary.isPresent()) {
            CredentialCredit updateCredit = this.buildCreditCredential(loan, opBeneficiary.get());
            updateCredit.setIdHistorical(idHistoricCredit); //assign the old historic.
            credentialCreditRepository.save(updateCredit);


            // if credit is finalized credential will be revoke
            if (loan.getStatus() == 60) { // its ok to use 60 state ?
                credit.setFinalizedTime(DateUtil.getLocalDateTimeNow().toLocalDate());
                //TODO No se revoca credito pero beneficio si, si este fuera el unico
            }
            else{
                if(loan.getIsDeleted()){
                    // TODO revoke and set to deleted the loan ?
                }
                else {
                    // validate the expired amount
                    List<CredentialCredit> creditGroup = credentialCreditRepository.findByIdGroup(loan.getIdGroup()); //TODO filter with active or pending credential
                    BigDecimal amountExpired = sumExpiredAmount(creditGroup);

                    Optional<ParameterConfiguration> config = parameterConfigurationRepository.findById(1L); //TODO Este ID asi no tiene que ir
                    if (config.isPresent()) {
                        BigDecimal maxAmount = new BigDecimal(Float.toString(config.get().getExpiredAmountMax()));
                        if (amountExpired.compareTo(maxAmount) > 0 ){
                            int cyclesExpired = updateCredit.getAmountExpiredCycles();
                            updateCredit.setAmountExpiredCycles(++cyclesExpired);
                            credentialCreditRepository.save(updateCredit);
                            //TODO revoke group credit and benefits
                        }
                        else{
                            //if credit has no expired amount
                            // try to create credential benefits in case holder does not have
                            this.createNewBenefitsCredential(opBeneficiary.get(), PersonTypesCodes.HOLDER);
                        }
                    }
                    else{
                        log.error("There is no configuration for getting the maximum expired amount.");
                        throw new NoExpiredConfigurationExists("There is no configuration for getting the maximum expired amount. Imposible to check the credential credit");
                    }
                }
            }
        }
        else {
            log.error("Person had been created and credential credit too, but person has been deleted eventually");
            throw new PersonDoesNotExists("Person had been created and credential credit too, but person has been deleted eventually");
        }
    }

      /**
     * Acumulate the expired amount of the credit group.
     * This able to check if the group is default.
     * @param group
     * @return
     */
    private BigDecimal sumExpiredAmount(List<CredentialCredit> group){

        BigDecimal amountExpired = BigDecimal.ZERO;

        for (CredentialCredit credit: group) {
            log.info("sumExpiredAmount: credit: "+credit.getExpiredAmount());
            amountExpired = amountExpired.add(new BigDecimal(Float.toString(credit.getExpiredAmount())));
            //todo: parece que hubiera que asignar el resultado de la suma a una variable.
        }

        log.info("sumExpiredAmount: sum: "+amountExpired.toString());

        return amountExpired;
    }

    /**
     * Revocation with the business logic.
     * For particular revocations use, this.revokeComplete()s
     * @param id
     * @return
     */
    public boolean revokeCredential(Long id) {
        boolean haveRevoke = true;

        log.info("Filtering credential with id: "+ id);
        //validate credential is in bd
        Optional<Credential> opCredential = credentialRepository.findById(id);
        if (opCredential.isPresent()) {
            //get the credential
            Credential credentialToRevoke = opCredential.get();
            CredentialTypesCodes credentialType;
            try {
                credentialType = CredentialTypesCodes.getEnumByStringValue(opCredential.get().getCredentialDescription());
            }
            catch (IllegalArgumentException ex){
                log.error("Impossible to revoke credential. There is no credential with type " + credentialToRevoke.getCredentialDescription());
                return false;
            }

            log.info("credential type of : "+ credentialType.getCode());
            switch (credentialType){
                case CREDENTIAL_DWELLING:
                case CREDENTIAL_ENTREPRENEURSHIP:
                case CREDENTIAL_BENEFITS_FAMILY:
                    this.revokeComplete(credentialToRevoke);
                    break;
                case CREDENTIAL_IDENTITY:
                    //find all the identities that the dni of the holder is into. (with state active or pending)
                    List<CredentialState> activePendingStates = credentialStateRepository.findByStateNameIn(List.of(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode(), CredentialStatesCodes.PENDING_DIDI.getCode()));
                    List<CredentialIdentity> holderIdentities = credentialIdentityRepository.findByCreditHolderDniAndCredentialStateIn(credentialToRevoke.getCreditHolderDni(), activePendingStates);

                    if(holderIdentities.size() == 0) {
                        log.info("There is no credential type "  + credentialType.getCode()+ " to revoke! The credentials are not in state pending or active");
                        haveRevoke = false;
                    }
                    for (Credential credential : holderIdentities) {
                        this.revokeComplete(credential);
                    }
                    break;
                case CREDENTIAL_IDENTITY_FAMILY:
                    //revoke the identities of the familiar: the one created by the survey and if it exists, the one created because the person download the app. ((with state active or pending)
                    activePendingStates = credentialStateRepository.findByStateNameIn(List.of(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode(), CredentialStatesCodes.PENDING_DIDI.getCode()));
                    List<CredentialIdentity> familiarIdentities = credentialIdentityRepository.findByCreditHolderDniAndBeneficiaryDniAndCredentialStateIn(credentialToRevoke.getCreditHolderDni(),
                                                                                        credentialToRevoke.getBeneficiaryDni(), activePendingStates);
                    if(familiarIdentities.size() == 0) {
                        log.info("There is no credential type " + credentialType.getCode() + " to revoke! The credentials are not in state pending or active");
                        haveRevoke = false;
                    }
                    for (Credential credential : familiarIdentities) {
                        this.revokeComplete(credential);
                    }
                    break;
                    
                case CREDENTIAL_BENEFITS:
                    //revoke if the holder does not have another credit and revoke benefits family, if the familiar does not have a credit too.
                    activePendingStates = credentialStateRepository.findByStateNameIn(List.of(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode(), CredentialStatesCodes.PENDING_DIDI.getCode()));
                    List<CredentialCredit> creditsActivePending = credentialCreditRepository.findByCreditHolderDniAndCredentialStateIn(credentialToRevoke.getCreditHolderDni(), activePendingStates);
                    if(creditsActivePending.size() == 0)
                        this.revokeComplete(credentialToRevoke);
                    else {
                         log.info("Impossible to revoke credential benefit. There is/are credential/s credit in state active or pending.");
                         haveRevoke = false;
                    }

                    //todo revoke benefits familiar using the dni holder and type familiar

                case CREDENTIAL_CREDIT:
                    //get the credit credential to get the group
                    Optional<CredentialCredit> credentialCredit = credentialCreditRepository.findById(credentialToRevoke.getId());
                    if(credentialCredit.isPresent()) {
                        List<CredentialCredit> creditsGroup = credentialCreditRepository.findByIdGroup(credentialCredit.get().getIdGroup());
                        //for each holder credit -> revoke credit -> revoke benefits -> revoke familiar benefits
                        for (CredentialCredit credit: creditsGroup) {
                             this.revokeComplete(credit);
                             //get benefits with holder dni (holder benefits and familiar benefits)
                             activePendingStates = credentialStateRepository.findByStateNameIn(List.of(CredentialStatesCodes.CREDENTIAL_ACTIVE.getCode(), CredentialStatesCodes.PENDING_DIDI.getCode()));
                            List<CredentialBenefits> benefits = credentialBenefitsRepository.findByCreditHolderDniAndCredentialStateIn(credit.getCreditHolderDni(), activePendingStates);
                            for (CredentialBenefits benefit : benefits) {
                                this.revokeComplete(benefit);
                            }
                        }

                    }
                    else{
                         log.error("Error you are trying to revoke ");
                         haveRevoke = false;
                    }
                    
            }

        } else {
            //todo throw non-existent credential ?
            log.error("Error you are trying to revoke ");
            haveRevoke = false;
        }

        return  haveRevoke;
    }

    /**
     * Revoke on DB and revoke on didi
     *
     * @param credentialToRevoke
     */
    private void revokeComplete(Credential credentialToRevoke){
        log.info("Starting revoking process for credential id: "+ credentialToRevoke.getId() + " | credential type: " + credentialToRevoke.getCredentialDescription());
        //here is important to manage the different actions, and need to be synchronize at the end.
        //todo call revoke on didi
        boolean revokedOnSemillas = this.revokeOneCredential(credentialToRevoke);
        // validate the whole transaction using revokedOnSemillas and then the check of didi
    }

    /**
     * Revoke only for internal usage. Only revokes the credential on the DB.
     *
     * @param credentialToRevoke
     * @return boolean
     */
    protected boolean revokeOneCredential(Credential credentialToRevoke) {
        log.info("Revoking the credential " + credentialToRevoke.getId());
        boolean haveRevoke = true;

        //validate if the credential is in db
        Optional<Credential> credential = credentialRepository.findById(credentialToRevoke.getId());
        if (credential.isEmpty()) {
            haveRevoke = false;
            log.error("The state revoke could not be found");
        }
        //get revoke state
        Optional<CredentialState> opStateRevoke = credentialStateRepository.findByStateName(CredentialStatesCodes.CREDENTIAL_REVOKE.getCode());
        if (opStateRevoke.isPresent()) {
            //revoke if the credential is not revoked yet
            if (credential.get().getCredentialState().equals(opStateRevoke.get())) {
                log.info("The credential " + credential.get().getId() + " has already been revoked");
                haveRevoke = false;
            }

            credentialToRevoke.setCredentialState(opStateRevoke.get());
            //todo: set the reason of revocation ?
            credentialToRevoke.setDateOfRevocation(DateUtil.getLocalDateTimeNow());
            credentialRepository.save(credentialToRevoke);
            log.info("Credential with id " + credentialToRevoke.getId() + " has been revoked!");
        } else {
            haveRevoke = false;
            log.error("The state revoke could not be found");
        }

        return haveRevoke;
    }


}

