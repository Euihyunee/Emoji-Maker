package com.emojiMaker.BackEnd.Service;

import com.emojiMaker.BackEnd.Model.DAO.UserDAO;
import com.emojiMaker.BackEnd.Model.UserResource;
import com.emojiMaker.BackEnd.Repository.UserDAORepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class LoginService {
    @Autowired
    UserDAORepository userDAORepository;
    private final Environment env;
    private final RestTemplate restTemplate = new RestTemplate();

    public LoginService(Environment env) {
        this.env = env;
    }

    public UserDAO socialLogin(String code, String registrationId) {
        log.info("======================================================");
        String accessToken = getAccessToken(code, registrationId);
        JsonNode userResourceNode = getUserResource(accessToken, registrationId);
        System.out.println(userResourceNode.get("id"));
        System.out.println("accessToken : " + accessToken);
        System.out.println("userResourceNode : " + userResourceNode);

        UserDAO userDAO = new UserDAO(userResourceNode.get("id").asText(),
                userResourceNode.get("email").asText(),
                userResourceNode.get("picture").asText(),
                accessToken);
        userDAORepository.save(userDAO);



        UserResource userResource = new UserResource();
        log.info("userResource = {}", userResource);
        switch (registrationId) {
            case "google": {
                userResource.setId(userResourceNode.get("id").asText());
                userResource.setEmail(userResourceNode.get("email").asText());
                userResource.setPicture(userResourceNode.get("picture").asText());
                break;
            } case "kakao": {
                userResource.setId(userResourceNode.get("id").asText());
                userResource.setEmail(userResourceNode.get("kakao_account").get("email").asText());
                userResource.setPicture(userResourceNode.get("kakao_account").get("profile").get("nickname").asText());
                break;
            } case "naver": {
                userResource.setId(userResourceNode.get("response").get("id").asText());
                userResource.setEmail(userResourceNode.get("response").get("email").asText());
                userResource.setPicture(userResourceNode.get("response").get("nickname").asText());
                break;
            } default: {
                throw new RuntimeException("UNSUPPORTED SOCIAL TYPE");
            }
        }
        log.info("id = {}", userResource.getId());
        log.info("email = {}", userResource.getEmail());
        log.info("picture {}", userResource.getPicture());
        log.info("======================================================");
        return userDAO;
    }

    private String getAccessToken(String authorizationCode, String registrationId) {
        String clientId = env.getProperty("oauth2." + registrationId + ".client-id");
        String clientSecret = env.getProperty("oauth2." + registrationId + ".client-secret");
        String redirectUri = env.getProperty("oauth2." + registrationId + ".redirect-uri");
        String tokenUri = env.getProperty("oauth2." + registrationId + ".token-uri");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", authorizationCode);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity entity = new HttpEntity(params, headers);

        ResponseEntity<JsonNode> responseNode = restTemplate.exchange(tokenUri, HttpMethod.POST, entity, JsonNode.class);
        JsonNode accessTokenNode = responseNode.getBody();
        return accessTokenNode.get("access_token").asText();
    }

    private JsonNode getUserResource(String accessToken, String registrationId) {
        String resourceUri = env.getProperty("oauth2."+registrationId+".resource-uri");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity entity = new HttpEntity(headers);
        return restTemplate.exchange(resourceUri, HttpMethod.GET, entity, JsonNode.class).getBody();
    }
}