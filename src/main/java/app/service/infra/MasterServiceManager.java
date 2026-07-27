package app.service.infra;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MasterServiceManager {

  private final WebClient webClient;

  public MasterServiceManager(WebClient webClient) {
    this.webClient = webClient;
  }

}
