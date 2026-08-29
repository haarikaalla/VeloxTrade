#pragma once

#include <functional>
#include <string>

namespace velox {

struct HttpRequest {
  std::string method;
  std::string path;
  std::string body;
};

struct HttpResponse {
  int status{200};
  std::string content_type{"application/json"};
  std::string body;
};

using RequestHandler = std::function<HttpResponse(const HttpRequest&)>;

// Small blocking HTTP/1.1 server used to expose the matching engine on the
// internal container network. Requests are capped in size and handled per
// connection on a worker thread.
class HttpServer {
 public:
  HttpServer(std::string bind_address, unsigned short port);
  ~HttpServer();

  HttpServer(const HttpServer&) = delete;
  HttpServer& operator=(const HttpServer&) = delete;

  // Blocks the calling thread until the process is terminated.
  bool serve(const RequestHandler& handler);

 private:
  std::string bind_address_;
  unsigned short port_;
};

}  // namespace velox
