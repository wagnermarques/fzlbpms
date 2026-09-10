package fzlbpms.contatemeantes;

public class PingServiceBean implements PingService {

	@Override
	public String pingJson() {
		return "{\"status\":\"ok\",\"bundle\":\"contatemeantes\"}";
	}

}
