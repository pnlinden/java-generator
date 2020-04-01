package de.fraunhofer.iais.eis.util;

import java.io.Serializable;
import java.net.URI;
import java.util.StringTokenizer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TypedLiteral implements Serializable {

	@JsonProperty("@value")
	private String value = "";

	@JsonProperty("@language")
	private String language = "";

	@JsonProperty("@type")
	private String type = "";

	public TypedLiteral() {
		super();
	}
	

	public TypedLiteral(String valueAndTypeOrLanguage) {
		StringTokenizer tokenizer = new StringTokenizer(valueAndTypeOrLanguage, "@");
		this.value = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
		if (tokenizer.hasMoreTokens()) {
			this.language = tokenizer.nextToken();
		} else {
			tokenizer = new StringTokenizer(valueAndTypeOrLanguage, "^^");
			this.value = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
			this.value = this.value.replace("\"", "");
			
			this.type = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "http://www.w3.org/2001/XMLSchema#string";
		}
	}

	public TypedLiteral(String value, String language) {
		this.value = value;
		this.language = language;
	}

	public TypedLiteral(String value, URI type) {
		this.value = value;
		this.type = type.toString();
	}

	public String getValue() {
		return value;
	}

	public String getLanguage() {
		return language;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getType() {
		return type;
	}


	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String toString() {
		String result = this.value;
		if (this.language != null && !this.language.isEmpty()) return "\"" + result + "\"@" + this.language;
		if (this.type != null && !this.type.isEmpty()) return "\"" + result + "\"^^" + this.type;
		return result;
	}
}
