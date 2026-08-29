#include "velox/http_server.hpp"

#include <atomic>
#include <cstring>
#include <iostream>
#include <thread>

#if defined(_WIN32)
#include <winsock2.h>
#include <ws2tcpip.h>
using socket_t = SOCKET;
constexpr socket_t kInvalidSocket = INVALID_SOCKET;
#define VELOX_CLOSE_SOCKET closesocket
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
using socket_t = int;
constexpr socket_t kInvalidSocket = -1;
#define VELOX_CLOSE_SOCKET ::close
#endif

namespace velox {
namespace {

constexpr std::size_t kMaxRequestBytes = 64 * 1024;

std::string status_text(int status) {
  switch (status) {
    case 200: return "OK";
    case 400: return "Bad Request";
    case 404: return "Not Found";
    case 405: return "Method Not Allowed";
    case 413: return "Payload Too Large";
    default: return "Internal Server Error";
  }
}

void send_all(socket_t client, const std::string& payload) {
  std::size_t sent = 0;
  while (sent < payload.size()) {
    const auto written = ::send(client, payload.data() + sent,
                                static_cast<int>(payload.size() - sent), 0);
    if (written <= 0) return;
    sent += static_cast<std::size_t>(written);
  }
}

std::size_t content_length_of(const std::string& headers) {
  const std::string lowered = [&headers] {
    std::string copy = headers;
    for (char& character : copy) character = static_cast<char>(::tolower(character));
    return copy;
  }();
  const auto marker = lowered.find("content-length:");
  if (marker == std::string::npos) return 0;
  try {
    return static_cast<std::size_t>(std::stoul(headers.substr(marker + 15)));
  } catch (const std::exception&) {
    return 0;
  }
}

void handle_client(socket_t client, const RequestHandler& handler) {
  std::string buffer;
  char chunk[4096];
  std::size_t header_end = std::string::npos;

  while (buffer.size() < kMaxRequestBytes) {
    const auto received = ::recv(client, chunk, sizeof(chunk), 0);
    if (received <= 0) break;
    buffer.append(chunk, static_cast<std::size_t>(received));
    header_end = buffer.find("\r\n\r\n");
    if (header_end == std::string::npos) continue;
    const std::size_t expected = header_end + 4 + content_length_of(buffer.substr(0, header_end));
    if (buffer.size() >= expected) break;
  }

  HttpResponse response;
  if (buffer.size() >= kMaxRequestBytes) {
    response.status = 413;
    response.body = R"({"error":"request too large"})";
  } else if (header_end == std::string::npos) {
    response.status = 400;
    response.body = R"({"error":"malformed request"})";
  } else {
    HttpRequest request;
    const auto method_end = buffer.find(' ');
    const auto path_end = buffer.find(' ', method_end + 1);
    if (method_end == std::string::npos || path_end == std::string::npos) {
      response.status = 400;
      response.body = R"({"error":"malformed request line"})";
    } else {
      request.method = buffer.substr(0, method_end);
      request.path = buffer.substr(method_end + 1, path_end - method_end - 1);
      request.body = buffer.substr(header_end + 4);
      response = handler(request);
    }
  }

  std::string payload = "HTTP/1.1 " + std::to_string(response.status) + " " +
                        status_text(response.status) + "\r\nContent-Type: " +
                        response.content_type + "\r\nContent-Length: " +
                        std::to_string(response.body.size()) +
                        "\r\nConnection: close\r\n\r\n" + response.body;
  send_all(client, payload);
  VELOX_CLOSE_SOCKET(client);
}

}  // namespace

HttpServer::HttpServer(std::string bind_address, unsigned short port)
    : bind_address_(std::move(bind_address)), port_(port) {
#if defined(_WIN32)
  WSADATA data;
  WSAStartup(MAKEWORD(2, 2), &data);
#endif
}

HttpServer::~HttpServer() {
#if defined(_WIN32)
  WSACleanup();
#endif
}

bool HttpServer::serve(const RequestHandler& handler) {
  const socket_t listener = ::socket(AF_INET, SOCK_STREAM, 0);
  if (listener == kInvalidSocket) {
    std::cerr << "engine: unable to create socket\n";
    return false;
  }

  int reuse = 1;
  ::setsockopt(listener, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&reuse),
               sizeof(reuse));

  sockaddr_in address{};
  address.sin_family = AF_INET;
  address.sin_port = htons(port_);
  if (::inet_pton(AF_INET, bind_address_.c_str(), &address.sin_addr) != 1) {
    std::cerr << "engine: invalid bind address " << bind_address_ << '\n';
    VELOX_CLOSE_SOCKET(listener);
    return false;
  }

  if (::bind(listener, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0 ||
      ::listen(listener, 64) != 0) {
    std::cerr << "engine: unable to bind port " << port_ << '\n';
    VELOX_CLOSE_SOCKET(listener);
    return false;
  }

  std::cout << "engine: listening on " << bind_address_ << ':' << port_ << std::endl;
  while (true) {
    const socket_t client = ::accept(listener, nullptr, nullptr);
    if (client == kInvalidSocket) continue;
    std::thread(handle_client, client, std::cref(handler)).detach();
  }
}

}  // namespace velox
