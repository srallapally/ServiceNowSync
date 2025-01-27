import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class ParseEntitlement {
    public static void main(String[] args) throws IOException {
        System.out.println("Hello World!");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File("/Users/sanjay.rallapally/IdeaProjects/ServiceNow/src/test/test.json"));
        // Get the glossary node
        JsonNode glossaryNode = rootNode.get("glossary");
        //System.out.println("input"+glossaryNode);
        Object value = extractValue(glossaryNode,"idx//entitlement/lob_owner");
        System.out.println(value);
    }
    private static Object extractValue(JsonNode node, String jsonPath) {
        JsonNode current = node;
        String[] tokens = jsonPath.split("/");
        System.out.println(Arrays.toString(tokens));
        for (int i = 0; i < tokens.length; i++) {
            String part = tokens[i];
            System.out.println(part);
            // If the current token is empty, combine it with the next token
            // so that a path like "//entitlement" becomes "/entitlement".
            if (part.isEmpty()) {
                if (i + 1 < tokens.length) {
                    part = "/" + tokens[++i];
                } else {
                    // No next token to combine; break or return null as needed
                    break;
                }
            }
            current = current.path(part);
        }

        // Return primitive or textual values properly
        if (current.isTextual())  return current.asText();
        if (current.isNumber())   return current.numberValue();
        if (current.isBoolean())  return current.asBoolean();
        if (current.isArray())    return current.toString();
        return current.toString();
    }
}
