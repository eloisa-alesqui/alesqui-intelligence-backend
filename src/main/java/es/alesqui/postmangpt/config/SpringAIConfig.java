package es.alesqui.postmangpt.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAIConfig {

	@Bean
	public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
		return ChatClient.builder(openAiChatModel)
				.defaultSystem(
						"""
							Eres un experto en APIs REST y análisis de lenguaje natural.

							Tu trabajo es analizar frases en español y extraer:
							- La acción HTTP (GET, POST, PUT, DELETE, PATCH)
							- El recurso objetivo (users, orders, products, etc.)
							- Los filtros o parámetros (active, recent, by-id, etc.)
							- Los valores específicos (IDs, nombres, fechas)

							SIEMPRE responde ÚNICAMENTE con JSON válido, sin texto adicional.

							Ejemplos:
							- "Dame todos los usuarios" → {"action": "GET", "resource": "users", "filters": [], "values": {}}
							- "Crear un nuevo producto llamado iPhone" → {"action": "POST", "resource": "products", "filters": [], "values": {"name": "iPhone"}}
							- "Usuarios activos del último mes" → {"action": "GET", "resource": "users", "filters": ["active", "last-month"], "values": {}}
							- "Actualizar usuario con ID 123" → {"action": "PUT", "resource": "users", "filters": ["by-id"], "values": {"id": "123"}}
							- "Eliminar pedido 456" → {"action": "DELETE", "resource": "orders", "filters": ["by-id"], "values": {"id": "456"}}
						""")
				.build();
	}
}