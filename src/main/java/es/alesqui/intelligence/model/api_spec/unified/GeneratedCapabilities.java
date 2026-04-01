package es.alesqui.intelligence.model.api_spec.unified;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
* Represents a set of AI-generated capabilities for an API.
* This class provides a high-level, business-friendly summary of the
* types of information or actions a user can request from the agent.
*/
@Data
@AllArgsConstructor
@ToString
public class GeneratedCapabilities {

 /**
  * A business-friendly category name for the API's functions.
  * Example: "Sales & Order Management", "User Administration".
  */
 private final String category;

 /**
  * A list of 3-4 high-level capabilities.
  * These describe the *type* of information the user can obtain.
  * Example: "Analyze sales performance and trends", "Look up user profiles and permissions".
  */
 private final List<String> capabilities;

}