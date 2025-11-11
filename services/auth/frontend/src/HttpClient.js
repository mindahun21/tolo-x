import axios from 'axios';

const httpClient = axios.create({
    baseURL: "http://localhost:8222/auth",
    withCredentials: true,
    headers:{
        "X-Client-Type":"web"
    }
});

export { httpClient };
