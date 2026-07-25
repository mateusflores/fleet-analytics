import { Machine } from "../models/machine/machine.model";

export interface PaginatedMachinesResponse {
    content: Machine[];
    totalElements: number;
    totalPages: number;
    pageable: {
        pageNumber: number;
        pageSize: number;
    };
    last: boolean;
    first: boolean;
    numberOfElements: number;
    size: number;
    number: number;
    sort: {
        sorted: boolean;
        unsorted: boolean;
        empty: boolean;
    };
    empty: boolean;
}