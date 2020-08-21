package com.atixlabs.semillasmiddleware.app.controller;

import com.atixlabs.semillasmiddleware.app.bondarea.service.LoanService;
import com.atixlabs.semillasmiddleware.app.dto.CredentialDto;
import com.atixlabs.semillasmiddleware.app.exceptions.CredentialException;
import com.atixlabs.semillasmiddleware.app.exceptions.CredentialNotExistsException;
import com.atixlabs.semillasmiddleware.app.exceptions.PersonDoesNotExistsException;
import com.atixlabs.semillasmiddleware.app.model.credential.Credential;
import com.atixlabs.semillasmiddleware.app.model.credential.ShareCredentialRequest;
import com.atixlabs.semillasmiddleware.app.model.credential.constants.CredentialStatesCodes;
import com.atixlabs.semillasmiddleware.app.model.credential.constants.CredentialTypesCodes;
import com.atixlabs.semillasmiddleware.app.model.provider.exception.InexistentProviderException;
import com.atixlabs.semillasmiddleware.app.processControl.exception.InvalidProcessException;
import com.atixlabs.semillasmiddleware.app.processControl.service.ProcessControlService;
import com.atixlabs.semillasmiddleware.app.service.CredentialService;
import com.atixlabs.semillasmiddleware.app.service.MailService;
import com.atixlabs.semillasmiddleware.app.service.ShareCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(CredentialController.URL_MAPPING_CREDENTIAL)
@CrossOrigin(origins = "*", methods= {RequestMethod.GET,RequestMethod.POST, RequestMethod.PATCH})
@Slf4j
public class CredentialController {

    public static final String URL_MAPPING_CREDENTIAL = "/credentials";

    private CredentialService credentialService;
    private LoanService loanService;
    private ProcessControlService processControlService;
    private ShareCredentialService shareCredentialService;

    @Autowired
    public CredentialController(CredentialService credentialService,
                                LoanService loanService,
                                ProcessControlService processControlService,
                                ShareCredentialService shareCredentialService) {
        this.credentialService = credentialService;
        this.loanService = loanService;
        this.processControlService = processControlService;
        this.shareCredentialService = shareCredentialService;
    }


    //TODO fix
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<CredentialDto> findCredentials(@RequestParam @DefaultValue("0") Integer page,
                                          @RequestParam(required = false) String credentialType,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) String dniBeneficiary,
                                          @RequestParam(required = false) String creditHolderDni,
                                          @RequestParam(required = false) String idDidiCredential,
                                          @RequestParam(required = false) String lastUpdate,
                                          @RequestParam(required = false) List<String> credentialState) {

        Page<CredentialDto> credentials;
        try {
            log.info("find credentials "+credentialType+" "+name+dniBeneficiary+" "+idDidiCredential+" "+lastUpdate+" "+credentialState.get(0));
            credentials = credentialService.findCredentials(credentialType, name, dniBeneficiary, creditHolderDni, idDidiCredential, lastUpdate, credentialState, page);
        } catch (Exception e) {
            log.info("There has been an error searching for credentials with the filters "+ credentialType + " " + name + " " + dniBeneficiary + " " + idDidiCredential + " " +
                    credentialState.toString() + " " + e);
            return null;
        }
        return credentials;
    }

    @GetMapping("/states")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> findCredentialStates() {
        Map<String, String> credentialStates = new HashMap<>();
        for (CredentialStatesCodes states : CredentialStatesCodes.values()) {
            credentialStates.put(states.name(), states.getCode());
        }

        return credentialStates;
    }

    @GetMapping("/types")
    @ResponseStatus(HttpStatus.OK)
    public List<String> findCredentialTypes() {
        List<String> credentialTypes = Arrays.stream(CredentialTypesCodes.values()).map(state -> state.getCode()).collect(Collectors.toList());
        return credentialTypes;
    }

    @GetMapping("/revocation-reasons")
    @ResponseStatus(HttpStatus.OK)
    public Map<Long, String> findRevocationReasons() {
        //todo this is not the best option to obtain the reasons able by the user.
        return credentialService.getRevocationReasonsForUser();
    }



    @PatchMapping("/revoke/{idCredential}/reason/{idReason}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<String> revokeCredential(@PathVariable @NotNull @Min(1) Long idCredential, @PathVariable @NotNull @Min(1) Long idReason) {
        Optional<String> opRevocationReason = credentialService.getReasonFromId(idReason);
        if (opRevocationReason.isPresent()) {
            Optional<Credential> credentialToRevoke = credentialService.getCredentialById(idCredential);
            if (credentialToRevoke.isPresent()) {
                if (credentialToRevoke.get().isManuallyRevocable()) {
                    //possibilities -> Emprendimiento, Vivienda, identididad familiar, identidad titular (only the last one have a business logic, the others only revoke itself)
                    boolean revokeOk = false;
                    try {
                        revokeOk = credentialService.revokeCredential(idCredential, opRevocationReason.get());
                    } catch (CredentialException e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error trying to revoke credential with id: " + idCredential);
                    }
                    if (revokeOk)
                        return ResponseEntity.status(HttpStatus.OK).body("Revoked successfully");
                    else
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error trying to revoke credential with id: " + idCredential);
                } else
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Credential with id: " + idCredential + " is not manually revocable");
            } else {
                log.error("There is no credential with id: " + idCredential);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("There is no credential with id: " + idCredential);
            }
        } else
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Is not possible to revoke with reason " + idReason);
    }



    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<String> generateCredentialsCredit() {
        try {
            credentialService.generateCreditAndBenefitsCredentialsByLoans();
        }
        catch (InvalidProcessException ex){
            log.error("Could not get the process ! "+ ex.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }


    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/share")
    public ResponseEntity<?> notifyProvider(@Valid @RequestBody ShareCredentialRequest shareCredentialRequest){
        if (!shareCredentialRequest.getCustomProviderEmail().isPresent() && !shareCredentialRequest.getProviderId().isPresent())
            return ResponseEntity.badRequest().body("You must either specify a provider id or an email");

        try{
            shareCredentialService.shareCredential(shareCredentialRequest);
        }catch (PersonDoesNotExistsException pdnee){
            log.error("Person with dni "+shareCredentialRequest.getDni()+" does not exist");
            return ResponseEntity.badRequest().body("person with dni "+shareCredentialRequest.getDni()+" not found");
        }catch (InexistentProviderException ipe){
            log.error("Provider with id %s does not exist", shareCredentialRequest.getProviderId());
            return ResponseEntity.badRequest().body("provider with id "+shareCredentialRequest.getProviderId()+" not found" );
        }catch (CredentialNotExistsException credEx){
            log.warn("There are no Benefit credentials emitted for the specified beneficiary and credit holder");
            return ResponseEntity.badRequest().body(credEx.getMessage());
        }catch (Exception ex){
            log.error("Could not send email message:" + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getStackTrace());
        }

        return ResponseEntity.ok().body("shared.");
    }

}
