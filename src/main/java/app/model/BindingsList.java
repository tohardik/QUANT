package app.model;

public class BindingsList {
	UriList uri;
	UriList c;
	UriList string;
	
	public BindingsList(){
		
	}
	
	public BindingsList(UriList uri){
		this.uri = uri;
	}
	
	public UriList getUri(){
		return uri;
	}
	
	public void setUri(UriList uri){
		this.uri = uri;
	}
	public UriList getC(){
		return c;
	}
	public UriList getString(){
		return string;
	}
}
