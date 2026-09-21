package fzlbpms.chamadasonline;

public class PingServiceBean implements PingService {

	@Override
	public String pingJson() {
		return "{\"ok\":true}";
	}

}
