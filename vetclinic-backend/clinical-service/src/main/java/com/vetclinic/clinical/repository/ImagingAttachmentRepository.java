package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.ImagingAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImagingAttachmentRepository extends JpaRepository<ImagingAttachment, Long> {
    java.util.List<ImagingAttachment> findByImagingResultIdIn(java.util.Collection<Long> imagingResultIds);
}
